import entities.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usecase.placebetanddeal.*;
import entities.Player;
import entities.Dealer;
import entities.Deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;

class PlaceBetAndDealTest {
    private PlaceBetAndDealDataAccessInterface dataAccess;
    private PlaceBetAndDealOutputBoundary presenter;

    private PlaceBetAndDealInteractor interactor;
    private Player player;
    private Dealer dealer;
    private Deck deck;

    @BeforeEach
    void setUp() {
        dataAccess = mock(PlaceBetAndDealDataAccessInterface.class);
        presenter = mock(PlaceBetAndDealOutputBoundary.class);

        deck = mock(Deck.class);
        player = new Player(1000);
        dealer = new Dealer(deck);

        when(dataAccess.getPlayer()).thenReturn(player);
        when(dataAccess.getDealer()).thenReturn(dealer);
        when(dataAccess.getDeck()).thenReturn(deck);

        interactor = new PlaceBetAndDealInteractor(dataAccess, presenter);
    }

    @Test
    void testAddReservedBetValid() {
        player.setBalance(1000);
        interactor.addReservedBet(100);
        assertEquals(900, player.getBalance());
        verify(presenter).presentBetUpdated(900, 100);
    }

    @Test
    void testAddReservedBetInvalid() {
        player.setBalance(1000);
        interactor.addReservedBet(1001);
        assertEquals(1000, player.getBalance());
        verify(presenter).presentError("Insufficient balance");
    }

    @Test
    void testClearReservedBetValid() {
        player.setBalance(1000);
        interactor.addReservedBet(500);
        interactor.clearReservedBet();
        assertEquals(1000, player.getBalance());
        verify(presenter).presentBetUpdated(1000, 0);
    }

    @Test
    void testAllInValid() {
        player.setBalance(1000);
        interactor.allIn();
        assertEquals(0, player.getBalance());
        verify(presenter).presentBetUpdated(0, 1000);
    }

    @Test
    void testAllInZeroBalance() {
        player.setBalance(0);
        interactor.allIn();
        verify(presenter).presentError("Insufficient balance");
    }

    @Test
    void testRestartGameValid() {
        player.setBalance(500);
        interactor.addReservedBet(100);
        interactor.restartGame();
        assertEquals(1000, player.getBalance());
        verify(presenter).presentBetUpdated(1000, 0);
    }

    @Test
    void testExecuteNoShuffle() {
        player.setBalance(1000);
        interactor.addReservedBet(100);

        when(deck.getRemainingCards()).thenReturn(100);  // make method return 100

        Card playerFirst = new Card("AS", "Spades", "Ace", 11, "");
        Card playerSecond = new Card("KS", "Spades", "King", 10, "");
        Card dealerFirst = new Card("AH", "Hearts", "Ace", 11, "");
        Card dealerSecond = new Card("KH", "Hearts", "King", 10, "");

        when(deck.drawCards(2)).thenReturn(List.of(playerFirst, playerSecond))
                .thenReturn(List.of(dealerFirst, dealerSecond));

        interactor.execute(new PlaceBetAndDealInputData(100));

        verify(presenter).present(any(PlaceBetAndDealOutputData.class));
        verify(deck, never()).shuffleDeck();
    }

    @Test
    void testExecuteWithShuffle() {
        player.setBalance(1000);
        interactor.addReservedBet(100);

        when(deck.getRemainingCards()).thenReturn(50);

        Card playerFirst = new Card("AS", "Spades", "Ace", 11, "");
        Card playerSecond = new Card("KS", "Spades", "King", 10, "");
        Card dealerFirst = new Card("AH", "Hearts", "Ace", 11, "");
        Card dealerSecond = new Card("KH", "Hearts", "King", 10, "");

        when(deck.drawCards(2)).thenReturn(List.of(playerFirst, playerSecond))
                .thenReturn(List.of(dealerFirst, dealerSecond));

        interactor.execute(new PlaceBetAndDealInputData(100));

        verify(presenter).present(any(PlaceBetAndDealOutputData.class));
        verify(deck).shuffleDeck();
    }

    @Test
    void testGetters() {
        assertEquals(deck, interactor.getDeck());
        assertEquals(player, interactor.getPlayer());
        assertEquals(dealer, interactor.getDealer());
    }

}
