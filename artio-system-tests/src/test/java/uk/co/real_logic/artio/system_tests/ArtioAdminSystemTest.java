/*
 * Copyright 2020 Monotonic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.system_tests;

import org.agrona.CloseHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.artio.FixGatewayException;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.admin.ArtioAdmin;
import uk.co.real_logic.artio.admin.ArtioAdminConfiguration;
import uk.co.real_logic.artio.admin.FixAdminSession;
import uk.co.real_logic.artio.engine.FixEngine;
import uk.co.real_logic.artio.library.LibraryConfiguration;
import uk.co.real_logic.artio.session.Session;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static uk.co.real_logic.artio.TestFixtures.launchMediaDriver;
import static uk.co.real_logic.artio.system_tests.SystemTestUtil.*;

public class ArtioAdminSystemTest extends AbstractGatewayToGatewaySystemTest
{
    private ArtioAdmin artioAdmin;

    @Before
    public void launch()
    {
        mediaDriver = launchMediaDriver();

        acceptingEngine = FixEngine.launch(acceptingConfig(port, ACCEPTOR_ID, INITIATOR_ID, nanoClock)
            .deleteLogFileDirOnStart(true));

        initiatingEngine = launchInitiatingEngine(libraryAeronPort, nanoClock);

        final LibraryConfiguration acceptingLibraryConfig = acceptingLibraryConfig(acceptingHandler, nanoClock);
        acceptingLibrary = connect(acceptingLibraryConfig);
        initiatingLibrary = newInitiatingLibrary(libraryAeronPort, initiatingHandler, nanoClock);
        testSystem = new TestSystem(acceptingLibrary, initiatingLibrary);
    }

    @After
    public void teardown()
    {
        CloseHelper.close(artioAdmin);
    }

    @Test
    public void shouldQuerySessionStatus()
    {
        connectSessions();
        acquireAcceptingSession();
        messagesCanBeExchanged();

        testSystem.awaitBlocking(() ->
        {
            launchArtioAdmin();
            assertFalse(artioAdmin.isClosed());

            final List<FixAdminSession> allFixSessions = artioAdmin.allFixSessions();
            assertThat(allFixSessions, hasSize(1));

            assertSessionEquals(acceptingSession, allFixSessions.get(0));

            artioAdmin.close();
            assertTrue(artioAdmin.isClosed());

            artioAdmin.close();
            assertTrue("Close not idempotent", artioAdmin.isClosed());
        });
    }

    @Test
    public void shouldQueryMultipleSessions()
    {
        connectSessions();
        acquireAcceptingSession();
        messagesCanBeExchanged();

        // Create another session that will then be disconnected
        Reply<Session> successfulReply = initiate(initiatingLibrary, port, INITIATOR_ID3, ACCEPTOR_ID);
        final Session offlineSession = completeConnectSessions(successfulReply);
        logoutSession(offlineSession);
        assertSessionDisconnected(offlineSession);

        // A second session, temporarily gateway managed
        successfulReply = initiate(initiatingLibrary, port, INITIATOR_ID2, ACCEPTOR_ID);
        final Session otherInitSession = completeConnectSessions(successfulReply);
        messagesCanBeExchanged(otherInitSession);
        assertThat(acceptingLibrary.sessions(), hasSize(1));

        testSystem.awaitBlocking(() ->
        {
            launchArtioAdmin();

            final List<FixAdminSession> allFixSessions = artioAdmin.allFixSessions();
            assertThat(allFixSessions, hasSize(3));

            FixAdminSession fixAdminSession = allFixSessions.get(0);
            assertTrue(fixAdminSession.isConnected());
            assertEquals(INITIATOR_ID2, fixAdminSession.sessionKey().remoteCompId());
            assertEquals(otherInitSession.lastSentMsgSeqNum(), fixAdminSession.lastReceivedMsgSeqNum());
            assertEquals(otherInitSession.lastReceivedMsgSeqNum(), fixAdminSession.lastSentMsgSeqNum());

            assertSessionEquals(acceptingSession, allFixSessions.get(1));

            fixAdminSession = allFixSessions.get(2);
            assertFalse(fixAdminSession.isConnected());
            assertEquals(INITIATOR_ID3, fixAdminSession.sessionKey().remoteCompId());
            assertEquals(2, fixAdminSession.lastReceivedMsgSeqNum());
            assertEquals(2, fixAdminSession.lastSentMsgSeqNum());
        });
    }

    @Test
    public void shouldDisconnectSession()
    {
        connectSessions();
        acquireAcceptingSession();
        messagesCanBeExchanged();

        testSystem.awaitBlocking(() ->
        {
            launchArtioAdmin();

            artioAdmin.disconnectSession(acceptingSession.id());
        });

        assertSessionsDisconnected();
    }

    @Test
    public void shouldThrowWhenUnknownDisconnectSession()
    {
        testSystem.awaitBlocking(() ->
        {
            launchArtioAdmin();

            assertThrows(FixGatewayException.class, () -> artioAdmin.disconnectSession(-1337L));
        });
    }

    private void assertSessionEquals(final Session session, final FixAdminSession adminSession)
    {
        assertEquals(session.connectionId(), adminSession.connectionId());
        assertEquals(session.connectedHost(), adminSession.connectedHost());
        assertEquals(session.connectedPort(), adminSession.connectedPort());
        assertEquals(session.id(), adminSession.sessionId());
        assertEquals(session.compositeKey().toString(), adminSession.sessionKey().toString());
        connectTimeRange.assertWithinRange(adminSession.lastLogonTime());
        assertEquals(session.lastReceivedMsgSeqNum(), adminSession.lastReceivedMsgSeqNum());
        assertEquals(session.lastSentMsgSeqNum(), adminSession.lastSentMsgSeqNum());
        assertTrue(adminSession.isConnected());
        assertFalse(adminSession.isSlow());
    }

    private void launchArtioAdmin()
    {
        final ArtioAdminConfiguration config = new ArtioAdminConfiguration();
        config.libraryAeronChannel(acceptingEngine.configuration().libraryAeronChannel());
        artioAdmin = ArtioAdmin.launch(config);
    }
}