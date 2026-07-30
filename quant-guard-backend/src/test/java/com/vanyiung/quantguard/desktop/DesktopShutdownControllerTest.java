package com.vanyiung.quantguard.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DesktopShutdownControllerTest {

    private final DesktopShutdownCoordinator coordinator = mock(DesktopShutdownCoordinator.class);

    @Test
    void acceptsLoopbackRequestWithMatchingToken() {
        DesktopShutdownController controller = new DesktopShutdownController(coordinator, "secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var response = controller.shutdown("secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(coordinator).requestShutdown();
    }

    @Test
    void rejectsInvalidToken() {
        DesktopShutdownController controller = new DesktopShutdownController(coordinator, "secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.shutdown("wrong", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verifyNoInteractions(coordinator);
    }

    @Test
    void rejectsNonLoopbackRequest() {
        DesktopShutdownController controller = new DesktopShutdownController(coordinator, "secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");

        assertThatThrownBy(() -> controller.shutdown("secret", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verifyNoInteractions(coordinator);
    }

    @Test
    void hidesEndpointWhenDesktopTokenIsNotConfigured() {
        DesktopShutdownController controller = new DesktopShutdownController(coordinator, "");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThatThrownBy(() -> controller.shutdown(null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verifyNoInteractions(coordinator);
    }
}
