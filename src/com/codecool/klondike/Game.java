package com.codecool.klondike;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import javax.swing.text.html.HTMLDocument;
import java.text.CollationElementIterator;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;

    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK) {
            saveMove(card);

            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        }
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK)
            return;
        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;

        draggedCards.clear();
        int draggedCardIndex = activePile.getCards().indexOf(card);

        activePile.getCards().listIterator(draggedCardIndex)
                  .forEachRemaining(draggedCards::add);

        draggedCards.forEach(draggedCard -> {
            draggedCard.getDropShadow().setRadius(20);
            draggedCard.getDropShadow().setOffsetX(10);
            draggedCard.getDropShadow().setOffsetY(10);

            draggedCard.toFront();
            draggedCard.setTranslateX(offsetX);
            draggedCard.setTranslateY(offsetY);
        });
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();

        Pile pile = getValidIntersectingPile(card, tableauPiles);
        if(pile == null) {
            pile = getValidIntersectingPile(card, foundationPiles);
        }

        //TODO
        if (pile != null) {
            saveMove(card);
          
            //TODO isOpositeColor
            handleValidMove(card, pile);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };

    public boolean isGameWon() {
        //TODO
        return false;
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();

        addRestartBtn();

        // ======= Added dummy for test ===============
        Button button = new Button();
        button.setText("Undo");
        button.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                Undoer.getInstance().undoAction();
            }
        });
        // ============================================

        getChildren().add(button);
    }

    public boolean canBeAutomaticallyEnd() {
        if (!(stockPile.isEmpty())) {
            return false;
        } else if (!(discardPile.isEmpty())) {
            return false;
        } else if(!(areTableCardsRevealed())){
            return false;
        }
        return true;
    }

    private boolean areTableCardsRevealed(){
        for (Pile pile:tableauPiles) {
            for (Card card:pile.getCards()) {
                if (card.isFaceDown()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void flipAllTableCardsFaceUp(){
        for (Pile pile:tableauPiles) {
            for (Card card:pile.getCards()) {
                if (card.isFaceDown()) {
                    card.flip();
                }
            }
        }
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        CopyOnWriteArrayList<Card> cards = new CopyOnWriteArrayList<>(discardPile.getCards());
        Collections.reverse(cards);

        cards.forEach(card -> card.flip());
        MouseUtil.slideToDest(cards, stockPile);

        System.out.println("Stock refilled from discard pile.");
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        if (foundationPiles.contains(destPile)) {
            return isValidMoveForFoundation(card, destPile);
        }
        else if (tableauPiles.contains(destPile))
            return isValidMoveForTableau(card, destPile);

        return false;
    }
    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);

        MouseUtil.slideToDest(draggedCards, destPile);
        draggedCards.clear();
    }

    private void saveMove(Card card) {
        List<Card> copyOfDraggedList = FXCollections.observableArrayList(draggedCards);
        Pile sourcePile = card.getContainingPile();
        Runnable move;


        switch (card.getContainingPile().getPileType()) {
            case STOCK:
                move = () -> {
                    card.moveToPile(sourcePile);
                    card.flip();
                };
                break;

            case DISCARD:
                move = () -> card.moveToPile(sourcePile);
                break;

            default:
                move = () -> {
                    if(!sourcePile.getTopCard().isFaceDown()) {
                        sourcePile.getTopCard().flip();
                    }

                    MouseUtil.slideToDest(copyOfDraggedList, sourcePile);
                };

        }

        Undoer.getInstance().addAction(Undoer.ActionOwner.USER, move);
    }

    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    private void addRestartBtn(){
        Button restartBtn = new Button("Restart");
        restartBtn.setTextAlignment(TextAlignment.CENTER);
        restartBtn.relocate(1300,840);
        restartBtn.setStyle("-fx-font: 18 times-new-roman; -fx-base: #c26573;");
        getChildren().add(restartBtn);

        restartBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                restart();
            }
        });
    }

    private void clearPane() {
        stockPile.clear();
        discardPile.clear();
        foundationPiles.clear();
        tableauPiles.clear();
        this.getChildren().clear();
    }

    private void restart() {
        clearPane();
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        addRestartBtn();
    }

    public void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();

        for (Pile pile: tableauPiles) {
            int index = tableauPiles.indexOf(pile);

            for(int i = 0; i < index+1; i++){
                Card card = deckIterator.next();
                pile.addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
                if(i==index){
                    card.flip();
                }
            }
        }

        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });

    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

    private boolean isValidMoveForFoundation(Card card, Pile foundationPile) {
        boolean result = false;
        if (foundationPile.getTopCard() == null) {
            if (card.getRank().getValue() == 1) result = true;
        } else {
            if (foundationPile.getTopCard().getRank().getValue() - card.getRank().getValue() == -1 &&
                    foundationPile.getTopCard().getSuit() == card.getSuit()) result = true;
        }
        return result;
    }

    private boolean isValidMoveForTableau(Card card, Pile tableauPile) {
        boolean result = false;
        if (tableauPile.getTopCard() == null && card.getRank().getValue() == 13) {result = true;
        }

        else if (tableauPile.getTopCard() != null) {
            if (tableauPile.getTopCard().getRank().getValue() - card.getRank().getValue() == 1) {
                if ((card.getSuit() == Suit.DIAMONDS || card.getSuit() == Suit.HEARTS) &&
                        (tableauPile.getTopCard().getSuit() == Suit.CLUBS || tableauPile.getTopCard().getSuit() == Suit.SPADES))
                    result = true;
                else if ((card.getSuit() == Suit.CLUBS || card.getSuit() == Suit.SPADES) &&
                        (tableauPile.getTopCard().getSuit() == Suit.DIAMONDS || tableauPile.getTopCard().getSuit() == Suit.HEARTS))
                    result = true;
            }
        }
        return result;
    }
}
