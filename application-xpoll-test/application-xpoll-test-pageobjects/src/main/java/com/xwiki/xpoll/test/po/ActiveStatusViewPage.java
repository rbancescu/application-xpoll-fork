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
package com.xwiki.xpoll.test.po;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.xwiki.test.ui.po.ViewPage;

/**
 * @version $Id$
 */
public class ActiveStatusViewPage extends ViewPage
{
    @FindBy(xpath = "//div[@id = 'xwikicontent']/p")
    public WebElement pollDescription;

    @FindBy(xpath = "//table[contains(@class, 'xpoll')]//tr[1]//th[position()>1]")
    public List<WebElement> proposals;

    @FindBy(xpath = "//table[contains(@class, 'xpoll')]//tr[contains(@class, 'polls-active')]//input")
    public List<WebElement> proposalVoteInputs;

    @FindBy(xpath = "//div[contains(@class, 'save')]/input")
    public WebElement saveButton;

    @FindBy(xpath = "//table[contains(@class, 'xpoll')]//tbody//tr//a")
    public List<WebElement> usersThatVotedTableRows;

    @FindBy(css = ".warningmessage")
    public WebElement emptyPageWarningMessage;

    @FindBy(css = ".warningmessage")
    public WebElement guestCannotChangeVoteWarningMessage;

    public ArrayList<String> pollProposals = new ArrayList<String>();

    public String getDescription()
    {
        return pollDescription.getText();
    }

    public void getProposals()
    {
        for (WebElement proposal : proposals) {
            pollProposals.add(proposal.getText().trim());
        }
    }

    public void voteProposal(int i) {
        proposalVoteInputs.get(i).click();
        saveButton.click();
        getDriver().waitUntilPageIsReloaded();
    }

    public void voteSetGuestName(String guestName)
    {
        proposalVoteInputs.get(0).sendKeys(guestName);
    }

    public String voteGetGuestName()
    {
        return proposalVoteInputs.get(0).getAttribute("value");
    }

    public WebElement getVoteInput(int i) {
        return proposalVoteInputs.get(i);
    }

    public int getNumberOfUsersThatAlreadyVotedFromTable() {
        return usersThatVotedTableRows.size();
    }

    public String getEmptyPageWarningMessage()
    {
        return emptyPageWarningMessage.getText();
    }

    public String getGuestCannotChangeVoteWarningMessage()
    {
        return guestCannotChangeVoteWarningMessage.getText();
    }

    public boolean searchIfUserIsInTable(String user) {
        return usersThatVotedTableRows.stream().anyMatch(vote -> user.equals(vote.getText()));
    }
}
