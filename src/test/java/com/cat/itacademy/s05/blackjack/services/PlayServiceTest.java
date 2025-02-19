package com.cat.itacademy.s05.blackjack.services;

import com.cat.itacademy.s05.blackjack.dto.PlayDTO;
import com.cat.itacademy.s05.blackjack.enums.Play;
import com.cat.itacademy.s05.blackjack.enums.PlayerStatus;
import com.cat.itacademy.s05.blackjack.enums.Rank;
import com.cat.itacademy.s05.blackjack.enums.Suit;
import com.cat.itacademy.s05.blackjack.exceptions.custom.InvalidPlayException;
import com.cat.itacademy.s05.blackjack.model.Card;
import com.cat.itacademy.s05.blackjack.model.Game;
import com.cat.itacademy.s05.blackjack.model.Player;
import com.cat.itacademy.s05.blackjack.model.PlayerInGame;
import com.cat.itacademy.s05.blackjack.utils.BlackjackHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PlayServiceTest {

    @InjectMocks
    private PlayService playService;

    @Mock private PlayerService mockPlayerService;
    @Mock private DeckService mockDeckService;
    @Mock private BlackjackHelper mockBlackjackHelper;

    private Game game;
    private PlayDTO playDTO;

    @BeforeEach
    void setUp(){
        game = new Game();
        game.setPlayers(new ArrayList<>());
        game.getPlayers().add(new PlayerInGame("1234", "test player"));
    }

    @Test
    void executePlay_GameIsConcluded_InvalidPlayException(){
        game.setConcluded(true);
        playDTO = new PlayDTO("1234", Play.INITIAL_BET, 10);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_ActivePlayerNotEqualsPlayPlayer_InvalidPlayException(){
        playDTO = new PlayDTO("1111", Play.INITIAL_BET, 10);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_PlayerHasNoBetAndPlayIsNotInitialBet_InvalidPlayException(){
        game.getPlayers().getFirst().setBet(0);
        playDTO = new PlayDTO("1234", Play.HIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_InitialBetPlayButBetIsZero_InvalidPlayException(){
        game.getPlayers().getFirst().setBet(0);
        playDTO = new PlayDTO("1234", Play.INITIAL_BET, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_InitialBetPlayButBetIsLessThanZero_InvalidPlayException(){
        game.getPlayers().getFirst().setBet(0);
        playDTO = new PlayDTO("1234", Play.INITIAL_BET, -10);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_InitialBetPlayButAlreadyHasBet_InvalidPlayException(){
        game.getPlayers().getFirst().setBet(50);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        playDTO = new PlayDTO("1234", Play.INITIAL_BET, 10);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }


    @Test
    void executePlay_InitialBetPlayerHasNoBet_PlayerGetsBetAndUpdatedStatus(){
        when(mockPlayerService.subtractMoney(anyString(), anyInt())).thenReturn(Mono.just(new Player()));

        game.getPlayers().add(new PlayerInGame("4321", "test player 2"));

        game.getPlayers().getFirst().setBet(0);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PENDING_BET);
        game.getPlayers().getLast().setBet(0);
        game.getPlayers().getLast().setStatus(PlayerStatus.PENDING_BET);

        playDTO = new PlayDTO("1234", Play.INITIAL_BET, 20);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(20, game1.getPlayers().getFirst().getBet());
                    assertEquals(PlayerStatus.WAITING_FOR_DEAL, game1.getPlayers().getFirst().getStatus());
                    assertEquals(1, game1.getActivePlayerIndex(), "Active player changes.");
                }).verifyComplete();
    }

    @Test
    void executePlay_AfterInitialBetAllPlayersHaveBet_CardsGetDealtAndUpdatedStatus(){
        when(mockPlayerService.subtractMoney(anyString(), anyInt())).thenReturn(Mono.just(new Player()));
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(Suit.CLUBS, Rank.KING)))
                .when(mockDeckService).dealCard(any(), anyList());

        game.getPlayers().add(new PlayerInGame("4321", "test player 2"));
        game.setActivePlayerIndex(1);

        game.getPlayers().getFirst().setBet(10);
        game.getPlayers().getFirst().setStatus(PlayerStatus.WAITING_FOR_DEAL);
        game.getPlayers().getLast().setBet(0);
        game.getPlayers().getLast().setStatus(PlayerStatus.PENDING_BET);

        playDTO = new PlayDTO("4321", Play.INITIAL_BET, 20);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(20, game1.getPlayers().getLast().getBet(), "Player gets bet");
                    assertEquals(PlayerStatus.PLAYING, game1.getPlayers().getFirst().getStatus(), "Player1 status update");
                    assertEquals(PlayerStatus.PLAYING, game1.getPlayers().getLast().getStatus(), "Player2 status update");
                    assertEquals(2, game1.getPlayers().getFirst().getCards().size(), "Player1 gets cards");
                    assertEquals(2, game1.getPlayers().getLast().getCards().size(), "Player2 gets cards");
                    assertEquals(2, game1.getCroupier().getCards().size(), "Croupier gets cards");
                    assertEquals(0, game1.getActivePlayerIndex(), "Active player changes.");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayHitNotBustNot21_CardGetsDealt(){
        when(mockBlackjackHelper.isBust(anyList())).thenReturn(false);
        when(mockBlackjackHelper.getHandValue(anyList())).thenReturn(18);
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(Suit.CLUBS, Rank.KING)))
                .when(mockDeckService).dealCard(any(), anyList());

        game.getPlayers().getFirst().setBet(10);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards().addAll(List.of(new Card(null, null), new Card(null, null)));

        playDTO = new PlayDTO("1234", Play.HIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(PlayerStatus.PLAYING, game1.getPlayers().getFirst().getStatus(), "status not changes");
                    assertEquals(3, game1.getPlayers().getFirst().getCards().size(), "Player gets 1 card");
                    assertFalse(game1.isConcluded());
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayHitAndBust_CardGetsDealtAndStatusSetToBust(){
        when(mockBlackjackHelper.isBust(anyList())).thenReturn(true);
        when(mockBlackjackHelper.getHandValue(anyList())).thenReturn(23);
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(Suit.CLUBS, Rank.KING)))
                .when(mockDeckService).dealCard(any(), anyList());

        game.getPlayers().getFirst().setBet(10);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards().addAll(List.of(new Card(null, null), new Card(null, null)));

        playDTO = new PlayDTO("1234", Play.HIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(PlayerStatus.BUST, game1.getPlayers().getFirst().getStatus(), "status is Bust");
                    assertEquals(3, game1.getPlayers().getFirst().getCards().size(), "Player gets 1 card");
                    assertTrue(game1.isConcluded(), "Game is concluded");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayHitAndCardsValueIs21_CardGetsDealtAndStatusSetToStand(){
        when(mockBlackjackHelper.isBust(anyList())).thenReturn(false);
        when(mockBlackjackHelper.getHandValue(anyList())).thenReturn(21);
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(Suit.CLUBS, Rank.KING)))
                .when(mockDeckService).dealCard(any(), anyList());

        game.getPlayers().getFirst().setBet(10);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards().addAll(List.of(new Card(null, null), new Card(null, null)));

        playDTO = new PlayDTO("1234", Play.HIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(PlayerStatus.STAND, game1.getPlayers().getFirst().getStatus(), "status is STAND");
                    assertEquals(3, game1.getPlayers().getFirst().getCards().size(), "Player gets 1 card");
                    assertTrue(game1.isConcluded(), "Game is concluded");
                }).verifyComplete();
    }

    @ParameterizedTest
    @CsvSource ({"DOUBLE", "SPLIT", "SURRENDER"})
    void executePlay_PlaysOnlyValidAfterInitialDealPlayedWith3Cards_InvalidPlayException(Play invalidPlay){
        game.getPlayers().getFirst().setBet(50);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(List.of(new Card(null, null), new Card(null, null), new Card(null, null)));
        playDTO = new PlayDTO("1234", invalidPlay, 10);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_PlayDoubleAndBust_BetDoublesAndCardGetsDealtAndStatusSetToBust(){
        when(mockPlayerService.subtractMoney(anyString(), anyInt())).thenReturn(Mono.just(new Player()));
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(null, null)))
                .when(mockDeckService).dealCard(any(), anyList());
        when(mockBlackjackHelper.isBust(anyList())).thenReturn(true);

        game.getPlayers().getFirst().setBet(50);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards().addAll(List.of(new Card(null, null), new Card(null, null)));
        playDTO = new PlayDTO("1234", Play.DOUBLE, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(PlayerStatus.BUST, game1.getPlayers().getFirst().getStatus(), "status is BUST");
                    assertEquals(3, game1.getPlayers().getFirst().getCards().size(), "Player gets 1 card");
                    assertEquals(100, game1.getPlayers().getFirst().getBet(), "Player bet doubles");
                    assertTrue(game1.isConcluded(), "Game is concluded");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayDoubleAndNotBust_BetDoublesAndCardGetsDealtAndStatusSetToStand(){
        when(mockPlayerService.subtractMoney(anyString(), anyInt())).thenReturn(Mono.just(new Player()));
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(null, null)))
                .when(mockDeckService).dealCard(any(), anyList());
        when(mockBlackjackHelper.isBust(anyList())).thenReturn(false);

        game.getPlayers().getFirst().setBet(50);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards().addAll(List.of(new Card(null, null), new Card(null, null)));
        playDTO = new PlayDTO("1234", Play.DOUBLE, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(PlayerStatus.STAND, game1.getPlayers().getFirst().getStatus(), "status is STAND");
                    assertEquals(3, game1.getPlayers().getFirst().getCards().size(), "Player gets 1 card");
                    assertEquals(100, game1.getPlayers().getFirst().getBet(), "Player bet doubles");
                    assertTrue(game1.isConcluded(), "Game is concluded");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlaySplitWithDifferentCards_InvalidPlayException(){
        game.getPlayers().getFirst().setBet(50);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        game.getPlayers().getFirst().getCards()
                .addAll(List.of(new Card(Suit.CLUBS, Rank.KING), new Card(Suit.CLUBS, Rank.EIGHT)));
        playDTO = new PlayDTO("1234", Play.SPLIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .expectError(InvalidPlayException.class)
                .verify();
    }

    @Test
    void executePlay_PlayValidSplit_BetGetsPayedPlayerAddedToGameCardsGetDealt(){
        when(mockPlayerService.subtractMoney(anyString(), anyInt())).thenReturn(Mono.just(new Player()));
        doAnswer(invocation -> ((List<Card>) invocation.getArguments()[1]).add(new Card(null, null)))
                .when(mockDeckService).dealCard(any(), anyList());

        int bet = 50;
        game.getPlayers().getFirst().setBet(bet);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        game.getPlayers().getFirst().setCards(new ArrayList<>());
        Card card1 = new Card(Suit.CLUBS, Rank.KING);
        Card card2 = new Card(Suit.HEARTS, Rank.KING);
        game.getPlayers().getFirst().getCards()
                .addAll(List.of(card1, card2));
        playDTO = new PlayDTO("1234", Play.SPLIT, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(2, game1.getPlayers().size(), "Player count increments in 1");
                    PlayerInGame p1 = game.getPlayers().getFirst();
                    PlayerInGame p2 = game.getPlayers().getLast();
                    assertEquals(p1, p2, "Player1 is the same as Player2");
                    assertEquals(PlayerStatus.PLAYING, p1.getStatus(), "P1 status is PLAYING");
                    assertEquals(PlayerStatus.PLAYING, p2.getStatus(), "P2 status is PLAYING");
                    assertEquals(card1, p1.getCards().getFirst(), "P1 keeps 1st card");
                    assertEquals(card2, p2.getCards().getFirst(), "P1 gets 2nd card");
                    assertEquals(2, p1.getCards().size(), "P1 gets 1 card");
                    assertEquals(2, p2.getCards().size(), "P2 gets 1 card");
                    assertEquals(bet, p1.getBet(), "P1 bet isn't modified");
                    assertEquals(bet, p2.getBet(), "P2 bets initial bet");
                    assertFalse(game1.isConcluded(), "Game in progress");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlaySurrender_StatusSetToSurrenderGameIsConcluded(){
        int bet = 50;
        game.getPlayers().getFirst().setBet(bet);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        List<Card> cards = new ArrayList<>();
        game.getPlayers().getFirst().setCards(cards);
        playDTO = new PlayDTO("1234", Play.SURRENDER, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    PlayerInGame player = game1.getPlayers().getFirst();
                    assertEquals(PlayerStatus.SURRENDER, player.getStatus(), "Player status is SURRENDER");
                    assertEquals(0, player.getCards().size(), "No card is dealt");
                    assertEquals(bet, player.getBet(), "Player's bet isn't modified");
                    assertTrue(game1.isConcluded(), "Game concludes");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayStand_StatusSetToStand(){
        int bet = 50;
        game.getPlayers().getFirst().setBet(bet);
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        List<Card> cards = new ArrayList<>();
        game.getPlayers().getFirst().setCards(cards);
        playDTO = new PlayDTO("1234", Play.STAND, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    PlayerInGame player = game1.getPlayers().getFirst();
                    assertEquals(PlayerStatus.STAND, player.getStatus(), "Player status is STAND");
                    assertEquals(0, player.getCards().size(), "No card is dealt");
                    assertEquals(bet, player.getBet(), "Player's bet isn't modified");
                    assertTrue(game1.isConcluded(), "Game concludes");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayMultiplayerGame_PlayersThatAreNotPlayingGetSkipped(){
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        PlayerInGame p2 =new PlayerInGame("12", "2nd Player");
        p2.setStatus(PlayerStatus.STAND);
        PlayerInGame p3 =new PlayerInGame("13", "3rd Player");
        p3.setStatus(PlayerStatus.SURRENDER);
        PlayerInGame p4 =new PlayerInGame("14", "3rd Player");
        p4.setStatus(PlayerStatus.PLAYING);
        game.getPlayers().addAll(List.of(p2, p3, p4));
        playDTO = new PlayDTO("1234", Play.STAND, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(3, game1.getActivePlayerIndex(), "Players with index 1 and 2 get skipped");
                    assertFalse(game1.isConcluded(), "Game continues");
                }).verifyComplete();
    }

    @Test
    void executePlay_PlayMultiplayerGame_PlayerListRestartsFromBeginningWhenEndIsReached(){
        game.getPlayers().getFirst().setStatus(PlayerStatus.BUST);
        PlayerInGame p2 =new PlayerInGame("12", "2nd Player");
        p2.setStatus(PlayerStatus.STAND);
        PlayerInGame p3 =new PlayerInGame("13", "3rd Player");
        p3.setStatus(PlayerStatus.PLAYING);
        PlayerInGame p4 =new PlayerInGame("14", "3rd Player");
        p4.setStatus(PlayerStatus.PLAYING);
        game.getPlayers().addAll(List.of(p2, p3, p4));
        game.setActivePlayerIndex(3);
        playDTO = new PlayDTO("14", Play.STAND, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertEquals(2, game1.getActivePlayerIndex(), "Not playing players get skipped");
                    assertFalse(game1.isConcluded(), "Game continues");
                }).verifyComplete();
    }

    @Test
    void executePlay_AllPlayersPass_GameEnds(){
        game.getPlayers().getFirst().setStatus(PlayerStatus.PLAYING);
        PlayerInGame p2 =new PlayerInGame("12", "2nd Player");
        p2.setStatus(PlayerStatus.STAND);
        PlayerInGame p3 =new PlayerInGame("13", "3rd Player");
        p3.setStatus(PlayerStatus.SURRENDER);
        PlayerInGame p4 =new PlayerInGame("14", "3rd Player");
        p4.setStatus(PlayerStatus.BUST);
        game.getPlayers().addAll(List.of(p2, p3, p4));
        playDTO = new PlayDTO("1234", Play.STAND, 0);

        StepVerifier.create(playService.executePlay(game, playDTO))
                .consumeNextWith(game1 -> {
                    assertTrue(game1.isConcluded(), "Game concludes");
                }).verifyComplete();
    }

}
