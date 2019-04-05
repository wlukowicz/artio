package uk.co.real_logic.artio.system_tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.session.Session;

import static org.junit.Assert.assertEquals;
import static uk.co.real_logic.artio.TestFixtures.cleanupMediaDriver;
import static uk.co.real_logic.artio.TestFixtures.launchMediaDriver;
import static uk.co.real_logic.artio.dictionary.generation.Exceptions.closeAll;
import static uk.co.real_logic.artio.system_tests.SystemTestUtil.*;

public class MultipleConnectionSystemTest extends AbstractGatewayToGatewaySystemTest
{
    @Before
    public void launch()
    {
        delete(ACCEPTOR_LOGS);

        mediaDriver = launchMediaDriver();

        launchAcceptingEngine();
        initiatingEngine = launchInitiatingEngine(libraryAeronPort);
        initiatingLibrary = newInitiatingLibrary(libraryAeronPort, initiatingHandler);
        testSystem = new TestSystem(initiatingLibrary);

        connectSessions();
    }

    @Test
    public void shouldSupportConnectionAfterAuthenticationFailure()
    {
        // on first session
        messagesCanBeExchanged();

        // Fail authentication with an invalid comp id
        final Reply<Session> failureReply =
            initiate(initiatingLibrary, port, "invalidSenderCompId", ACCEPTOR_ID);
        testSystem.awaitReply(failureReply);

        initiatingSession = failureReply.resultIfPresent();

        assertEquals(Reply.State.ERRORED, failureReply.state());
        assertEquals("UNABLE_TO_LOGON: Disconnected before session active", failureReply.error().getMessage());

        // Complete a second connection
        final Reply<Session> successfulReply = initiate(initiatingLibrary, port, INITIATOR_ID2, ACCEPTOR_ID);
        completeConnectSessions(successfulReply);

        messagesCanBeExchanged();
    }

    @After
    public void shutdown()
    {
        closeAll(
            initiatingLibrary,
            acceptingLibrary,
            initiatingEngine,
            acceptingEngine,
            () -> cleanupMediaDriver(mediaDriver));
    }
}