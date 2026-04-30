package usecase.playeraction;

import entities.Deck;
import entities.Player;
import entities.Dealer;
import entities.Hand;
import entities.Card;
import usecase.dealeraction.*;

import java.util.ArrayList;
import java.util.List;

public class PlayerActionInteractor implements PlayerActionInputBoundary {

    private final Deck deck;
    private final Player player;
    private final Dealer dealer;
    private final PlayerActionOutputBoundary presenter;
    private final DealerActionInteractor dealerInteractor;
    private final PlayerActionInputData inputData;
    private boolean tookInsurance;

    public PlayerActionInteractor(Deck deck, Player player, Dealer dealer, PlayerActionOutputBoundary presenter,
                                  DealerActionInteractor dealerInteractor, PlayerActionInputData inputData) {
        this.deck = deck;
        this.player = player;
        this.dealer = dealer;
        this.presenter = presenter;
        this.dealerInteractor = dealerInteractor;
        this.inputData = inputData;
    }

    @Override
    public void hit() {
        try {
            double balance = player.getBalance();
            double betAmount = player.getCurrentBet();
            Hand hand = player.getCurrentHand();
            Card drawnCard = deck.drawCard();
            hand.addCard(drawnCard);

            int playerTotal = hand.getTotalPoints();
            boolean bust = hand.isBust();
            int dealerVisibleTotal = dealer.getHand().getCards().get(1).getValue();

            List<String> newPlayerImages = new ArrayList<>();
            for (Card c : hand.getCards()) {
                newPlayerImages.add(c.getImage());
            }


            PlayerActionOutputData outputData = new PlayerActionOutputData(
                newPlayerImages,
                null,
                playerTotal,
                dealerVisibleTotal,
                bust,
                hand.isBlackjack(),
                bust, // actionComplete true if bust
                balance,
                betAmount
            );

            presenter.present(outputData);

            // auto-stand if player hit 21
            if (playerTotal == 21) {
                stand();
            }
        } catch (Exception e) {
            presenter.presentError("Error during hit: " + e.getMessage());
        }
    }

    @Override
    public void stand() {
        double balance = player.getBalance();
        double betAmount = player.getCurrentBet();
        Hand hand = player.getCurrentHand();
        int dealerVisibleTotal = dealer.getHand().getCards().get(1).getValue();

        PlayerActionOutputData outputData = new PlayerActionOutputData(
                null,
                null,
                hand.getTotalPoints(),
                dealerVisibleTotal,
                hand.isBust(),
                hand.isBlackjack(),
                true,
                balance,
                betAmount
        );

        presenter.present(outputData);

        // Trigger dealer play on a separate thread so GUI stays responsive
        new Thread(dealerInteractor::dealerPlay).start();
    }

    @Override
    public void doubleDown() {
        if (player.getCurrentBet() > player.getBalance()) {
            presenter.presentError("Insufficient funds to double down.");
        }
        else if (player.getCurrentHand().getCards().size() > 2) {
            presenter.presentError("You cannot double down after hitting.");
        }
        else {
            double balance = player.getBalance();
            double betAmount = player.getCurrentBet();
            double newBalance = balance - betAmount;
            double newBetAmount = betAmount * 2;

            // draw just one more card
            Hand hand = player.getCurrentHand();
            Card drawnCard = deck.drawCard();
            hand.addCard(drawnCard);

            int playerTotal = hand.getTotalPoints();
            boolean bust = hand.isBust();
            int dealerVisibleTotal = dealer.getHand().getCards().get(1).getValue();

            List<String> newPlayerImages = new ArrayList<>();
            for (Card c : hand.getCards()) {
                newPlayerImages.add(c.getImage());
            }

            // update Player
            player.setBalance(newBalance);
            player.setCurrentBet(newBetAmount);

            PlayerActionOutputData outputData = new PlayerActionOutputData(
                    newPlayerImages,
                    null,
                    playerTotal,
                    dealerVisibleTotal,
                    bust,
                    hand.isBlackjack(),
                    true, // actionComplete true if doubling down
                    newBalance,
                    newBetAmount
            );

            presenter.present(outputData);

            // If player not bust, trigger dealer play on a separate thread so GUI stays responsive
            if (!bust) {
                new Thread(dealerInteractor::dealerPlay).start();
            }
        }
    }

    @Override
    public void split() {
        // TODO: implement split
        presenter.presentError("Split not implemented yet");
    }

    @Override
    public void insurance() {
        Hand dealerHand = dealer.getHand();
        Hand playerHand = player.getCurrentHand();

        int dealerVisibleValue = dealerHand.getCards().get(1).getValue();

        // insurance is only allowed if dealer is showing Ace
        if (dealerVisibleValue != 11) {
            presenter.presentError("You can only take insurance when the dealer shows an Ace");
            return;
        }

        // insurance can only be taken once, and before any other action
        if (playerHand.getCards().size() > 2 || tookInsurance) {
            presenter.presentError("You cannot take insurance at this time.");
            return;
        }

        // check if user has sufficient funds
        double insuranceCost = player.getCurrentBet() * 0.5;
        if (player.getBalance() < insuranceCost) {
            presenter.presentError("Insufficient funds");
            return;
        }

        // deduct insurance immediately
        player.setBalance(player.getBalance() - insuranceCost);
        tookInsurance = true;

        // Update UI after buying insurance
        // Since insurance is a side bet, initial bet amount remains unchanged
        presenter.present(new PlayerActionOutputData(
                null, null,
                playerHand.getTotalPoints(),
                dealerVisibleValue,
                playerHand.isBust(),
                playerHand.isBlackjack(),
                false,
                player.getBalance(),
                player.getCurrentBet()
        ));

        // check if dealer has blackjack
        if (dealerHand.isBlackjack()) {
            // Insurance pays 2:1, so player gets insuranceCost * 3 back in total
            double payout = insuranceCost * 3;
            player.setBalance(player.getBalance() + payout);

            presenter.presentResult(
                    "Dealer has Blackjack! Insurance pays out.",
                    player.getBalance(),
                    player.getCurrentBet()
            );

            stand();
        }

        // If dealer does NOT have blackjack:
        else {
            presenter.presentResult(
                    "Dealer does not have blackjack. Insurance lost.",
                    player.getBalance(),
                    player.getCurrentBet()
            );
        }
    }


    @Override
    public void handleRoundResult() {
        Hand playerHand = player.getCurrentHand();
        Hand dealerHand = dealer.getHand();
        double balance = player.getBalance();
        double betAmount = player.getCurrentBet();

        int playerScore = playerHand.getTotalPoints();
        int dealerScore = dealerHand.getTotalPoints();

        boolean playerBlackjack = playerHand.isBlackjack();
        boolean dealerBlackjack = dealerHand.isBlackjack();
        boolean playerBust = playerHand.isBust();
        boolean dealerBust = dealerHand.isBust();

        double payout = 0;
        String message;

        if (playerBust) {
            // player loses, no return
            payout = 0;
            message = "Player busts! Dealer wins.";
        }
        else if (playerBlackjack && !dealerBlackjack) {
            payout = betAmount * 2.5; // 3:2 blackjack
            message = "Blackjack! You win!";
        }
        else if (dealerBust) {
            payout = betAmount * 2;
            message = "Dealer busts! You win!";
        }
        else if (!playerBlackjack && dealerBlackjack) {
            payout = 0;
            message = "Dealer has blackjack. You lose.";
        }
        else if (playerScore > dealerScore) {
            payout = betAmount * 2;
            message = "You win!";
        }
        else if (dealerScore > playerScore) {
            payout = 0;
            message = "Dealer wins.";
        }
        else {
            // push, return bet only
            payout = betAmount;
            message = "Push.";
        }

        double newBalance = balance + payout;

        // update Player entity
        player.setBalance(newBalance);
        player.setCurrentBet(0);

        // push into ViewModel
        presenter.presentResult(message, newBalance, 0);
    }

    @Override
    public void canEndImmediately() {
        Hand playerHand =  player.getCurrentHand();
        boolean playerHasBlackjack = playerHand.isBlackjack();
        int dealerVisibleTotal = dealer.getHand().getCards().get(1).getValue();
        int dealerTotal = dealer.getHand().getTotalPoints();
        if (playerHasBlackjack) {
            if (dealerVisibleTotal < 10) {
                stand();
            }
            else if (dealerVisibleTotal == 10) {
                if (dealerTotal < 21) {
                    stand();
                }
                else {
                    // this is a draw
                    stand();
                }
            }
            else {  // dealer showing an ace
                // do nothing, player may still take insurance
            }
        }
        else {
            if (dealerVisibleTotal == 10 && dealerTotal == 21) {  // player doesn't have blackjack and dealer does
                stand();
            }
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Deck getDeck() {
        return deck;
    }

    public Dealer getDealer() {
        return dealer;
    }
}
