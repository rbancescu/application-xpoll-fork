/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.xpoll.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;

import com.xwiki.xpoll.rest.model.jaxb.Vote;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.xpoll.PollResultsCalculator;
import com.xwiki.xpoll.XPollException;
import com.xwiki.xpoll.XPollManager;

/**
 * Provides methods to interact with the polls of a XWiki page.
 *
 * @version $Id$
 * @since 2.1
 */
@Component
@Singleton
public class DefaultXPollManager implements XPollManager
{
    static final String XPOLL_SPACE_NAME = "XPoll";

    static final LocalDocumentReference XPOLL_CLASS_REFERENCE =
        new LocalDocumentReference(XPOLL_SPACE_NAME, "XPollClass");

    static final LocalDocumentReference XPOLL_VOTES_CLASS_REFERENCE =
        new LocalDocumentReference(XPOLL_SPACE_NAME, "XPollVoteClass");

    static final String PROPOSALS = "proposals";

    static final String VOTES = "votes";

    static final String USER = "user";

    static final String GUEST_ID = "guestId";

    static final String COOKIE_NAME = "poll_publicity_id";

    static final String XPOLL_TYPE = "type";

    static final String XPOLL_TYPE_MULTI = "multi";

    static final String MISSING_XPOLL_OBJECT_MESSAGE = "The document [%s] does not have a poll object.";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Override
    public void vote(DocumentReference docReference, DocumentReference user, Vote vote)
        throws XPollException
    {
        XWikiContext context = contextProvider.get();
        XWikiDocument document;
        try {
            document = context.getWiki().getDocument(docReference, context).clone();
            setUserVotes(vote, context, document, user);
        } catch (XWikiException e) {
            throw new XPollException(String.format("Failed to vote for [%s] on behalf of [%s].", docReference, user),
                e);
        }
    }

    @Override
    public String getRestURL(DocumentReference documentReference)
    {

        String contextPath = contextProvider.get().getRequest().getContextPath();
        StringBuilder stringBuilder = new StringBuilder();
        try {
            stringBuilder.append(contextPath);
            stringBuilder.append("/rest/wikis/");
            stringBuilder
                .append(URLEncoder.encode(documentReference.getWikiReference().getName(), XWiki.DEFAULT_ENCODING));
            for (SpaceReference spaceReference : documentReference.getSpaceReferences()) {
                stringBuilder.append("/spaces/");
                stringBuilder.append(URLEncoder.encode(spaceReference.getName(), XWiki.DEFAULT_ENCODING));
            }
            stringBuilder.append("/pages/");
            stringBuilder.append(URLEncoder.encode(documentReference.getName(), XWiki.DEFAULT_ENCODING));
            stringBuilder.append("/xpoll");
            return stringBuilder.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(String.format("Failed to retrieve the REST URL of the document: [%s]",
                documentReference), e);
        }
    }

    @Override
    public Map<String, Integer> getVoteResults(DocumentReference documentReference) throws XPollException
    {
        XWikiContext context = contextProvider.get();
        try {
            XWikiDocument doc = context.getWiki().getDocument(documentReference, context);
            BaseObject xpollObj = doc.getXObject(XPOLL_CLASS_REFERENCE);
            if (xpollObj == null) {
                throw new XPollException(String.format(MISSING_XPOLL_OBJECT_MESSAGE,
                    documentReference));
            }
            String pollType = xpollObj.getStringValue(XPOLL_TYPE);
            if (pollType.isEmpty()) {
                xpollObj.set(XPOLL_TYPE_MULTI, XPOLL_TYPE_MULTI, context);
                pollType = XPOLL_TYPE_MULTI;
            }
            return getXPollResults(documentReference, pollType);
        } catch (XWikiException e) {
            throw new XPollException(String
                .format("Failed to retrieve the vote results for poll [%s]. Root cause: [%s].", documentReference,
                    ExceptionUtils.getRootCauseMessage(e)));
        }
    }

    private void setUserVotes(Vote vote, XWikiContext context, XWikiDocument doc, DocumentReference user)
        throws XWikiException
    {
        String currentUserName = serializer.serialize(user, doc.getDocumentReference().getWikiReference());
        Cookie cookie = context.getRequest().getCookie(COOKIE_NAME);

        BaseObject xpollVoteOfCurrentUser = getXPollVoteForCurrentUser(doc, currentUserName, context, cookie);

        List<String> filteredProposals = vote.getProposals().stream()
            .filter(p -> !p.isEmpty())
            .collect(Collectors.toList());

        if (cookie == null && currentUserName == null) {
            cookie = createCookie(context);
        }

        if (currentUserName != null) {
            xpollVoteOfCurrentUser.set(USER, currentUserName, context);
        } else {
            xpollVoteOfCurrentUser.set(USER, vote.getGuestName(), context);
        }

        xpollVoteOfCurrentUser.set(VOTES, filteredProposals, context);

        if (cookie != null && currentUserName == null) {
            xpollVoteOfCurrentUser.set(GUEST_ID, cookie.getValue(), context);
        } else {
            xpollVoteOfCurrentUser.set(GUEST_ID, null, context);
        }

        context.getWiki().saveDocument(doc, "New Vote", context);
    }

    private BaseObject getXPollVoteForCurrentUser(XWikiDocument doc, String currentUserName, XWikiContext context,
        Cookie cookie) throws XWikiException
    {
        BaseObject xpollVoteOfCurrentUser = doc.getXObject(XPOLL_VOTES_CLASS_REFERENCE, USER,
            currentUserName, false);

        if (currentUserName == null && cookie != null) {
            xpollVoteOfCurrentUser = doc.getXObject(XPOLL_VOTES_CLASS_REFERENCE, GUEST_ID, cookie.getValue(), false);
        }

        if (xpollVoteOfCurrentUser == null) {
            xpollVoteOfCurrentUser = doc.newXObject(XPOLL_VOTES_CLASS_REFERENCE, context);
        }
        return xpollVoteOfCurrentUser;
    }

    private Cookie createCookie(XWikiContext context)
    {
        Cookie cookie = new Cookie(COOKIE_NAME, UUID.randomUUID().toString());
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setPath("/");
        context.getResponse().addCookie(cookie);
        return cookie;
    }

    private Map<String, Integer> getXPollResults(DocumentReference documentReference, String pollType)
        throws XPollException
    {
        try {
            ComponentManager componentManager = componentManagerProvider.get();
            PollResultsCalculator calculator = componentManager.getInstance(PollResultsCalculator.class,
                pollType);
            return calculator.getResults(documentReference);
        } catch (ComponentLookupException e) {
            throw new XPollException(String.format(
                "The results could not be calculated because the poll type [%s] lacks an implementation.", pollType));
        }
    }
}
