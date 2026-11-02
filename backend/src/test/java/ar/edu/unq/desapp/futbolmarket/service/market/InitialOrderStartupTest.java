package ar.edu.unq.desapp.futbolmarket.service.market;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitialOrderStartupTest {

    @Mock
    private InitialOrderService initialOrderService;

    @Test
    void elArranqueDelegaEnLaEntradaDeArranqueDelServicio() {
        InitialOrderStartup startup = new InitialOrderStartup(initialOrderService);

        startup.openInitialOrders();

        verify(initialOrderService).onStartup();
        verifyNoMoreInteractions(initialOrderService);
    }
}
