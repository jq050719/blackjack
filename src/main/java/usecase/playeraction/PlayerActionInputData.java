package usecase.playeraction;

public class PlayerActionInputData {
    private final boolean playerHasBlackjack;
    private final boolean dealerShowingTen;

    public PlayerActionInputData(boolean playerHasBlackjack, boolean dealerShowingTen) {
        this.playerHasBlackjack = playerHasBlackjack;
        this.dealerShowingTen = dealerShowingTen;
    }

    public boolean getPlayerHasBlackjack() {
        return playerHasBlackjack;
    }

    public boolean getDealerShowingTen() {
        return dealerShowingTen;
    }
}
