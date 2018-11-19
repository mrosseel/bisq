package bisq.core.trade;

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.Mediator;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.handlers.TradeResultHandler;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.Clock;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import javafx.collections.FXCollections;

import java.security.KeyPair;

import java.nio.file.Files;

import java.io.File;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;



import javax.validation.ValidationException;
import org.mockito.Mockito;

public class TradeManagerOnOfferTakeTest {

    private TradeResultHandler tradeResultHandlerMock;
    private ErrorMessageHandler errorMessageHandlerMock;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private KeyRing keyRing;
    private BtcWalletService btcWalletService;
    private BsqWalletService bsqWalletService;
    private TradeWalletService tradeWalletService;
    private OpenOfferManager openOfferManager;
    private ClosedTradableManager closedTradableManager;
    private FailedTradesManager failedTradesManager;
    private P2PService p2PService;
    private PriceFeedService priceFeedService;
    private FilterManager filterManager;
    private TradeStatisticsManager tradeStatisticsManager;
    private ReferralIdService referralIdService;
    private PersistenceProtoResolver persistenceProtoResolver;
    private AccountAgeWitnessService accountAgeWitnessService;
    private ArbitratorManager arbitratorManager;
    private Clock clock;
    private File storageDir;
    private User user;
    private AliPayAccount usersPaymentAccount;
    private Offer offerToCreate;

    @Before
    public void setUp() throws Exception {
        Res.setup();//TODO this is ugly! We should be able inject Res instance as constructor param into TradeManager
        PaymentMethod.onAllServicesInitialized();
        usersPaymentAccount = new AliPayAccount();
        usersPaymentAccount.init();
        Assert.assertNotNull(usersPaymentAccount.getId());

        final Arbitrator arbitratorA = new Arbitrator(new NodeAddress("arbitratorA", 0), null, null, null, null, 0, null, null, null, null, null);
        final Mediator mediatorA = new Mediator(new NodeAddress("mediatorA", 1), null, null, 0, null, null, null, null, null);

        keyRing = mock(KeyRing.class);
        when(keyRing.getSignatureKeyPair()).thenReturn(Sig.generateKeyPair());
        final KeyPair keyPair = Sig.generateKeyPair();
        final PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        when(keyRing.getPubKeyRing()).thenReturn(pubKeyRing);

        btcWalletService = mock(BtcWalletService.class);
        final AddressEntry addressEntry = new AddressEntry(mock(DeterministicKey.class), AddressEntry.Context.OFFER_FUNDING);
//        TODO stub more precisely, ideally with exact values or at least matchers
        when(btcWalletService.getOrCreateAddressEntry(any(), any())).thenReturn(addressEntry);
        when(btcWalletService.getFreshAddressEntry()).thenReturn(addressEntry);

        bsqWalletService = null;
        openOfferManager = null;
        closedTradableManager = null;
        failedTradesManager = null;
        p2PService = mock(P2PService.class);
        priceFeedService = null;
        filterManager = mock(FilterManager.class);

        tradeWalletService = mock(TradeWalletService.class);
        when(tradeWalletService.createBtcTradingFeeTx(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenAnswer(invocation -> {
            final Transaction transactionMock = mock(Transaction.class);
            when(transactionMock.getHashAsString()).thenReturn("transactionHashAsString");
            final TxBroadcaster.Callback callback = invocation.getArgument(8);
            callback.onSuccess(transactionMock);
            return transactionMock;
        });
        when(tradeWalletService.takerCreatesDepositsTxInputs(any(), any(), any(), any())).thenReturn(new InputsAndChangeOutput(Collections.singletonList(new RawTransactionInput(0, new byte[0], 0)), 0, null));


        tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet());

        referralIdService = null;
        persistenceProtoResolver = null;
        accountAgeWitnessService = null;

        arbitratorManager = mock(ArbitratorManager.class);
        when(arbitratorManager.getArbitratorsObservableMap()).thenReturn(FXCollections.observableMap(Collections.singletonMap(arbitratorA.getNodeAddress(), arbitratorA)));

        clock = null;

//        TODO it would be good if this test did not create any files
        storageDir = Files.createTempDirectory("tradeManagerTest").toFile();

        user = mock(User.class);
        when(user.getPaymentAccount(usersPaymentAccount.getId())).thenReturn(usersPaymentAccount);
        when(user.getAcceptedArbitratorAddresses()).thenReturn(Collections.singletonList(arbitratorA.getNodeAddress()));
        when(user.getAcceptedArbitratorByAddress(arbitratorA.getNodeAddress())).thenReturn(arbitratorA);
        when(user.getAcceptedMediatorByAddress(mediatorA.getNodeAddress())).thenReturn(mediatorA);
        when(user.getAcceptedMediatorAddresses()).thenReturn(Collections.singletonList(mediatorA.getNodeAddress()));
        when(user.getAccountId()).thenReturn("userAccountId");

        errorMessageHandlerMock = mock(ErrorMessageHandler.class);
        tradeResultHandlerMock = mock(TradeResultHandler.class);

        offerToCreate = createOffer("offerId", arbitratorA, mediatorA);
    }

    @Test
    public void onTakeOffer_paymentAccountIdIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Payment account for given id does not exist: null");
        final OnTakeOfferParams params = getValidParams();
        params.paymentAccountId = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_noPaymentAccountForGivenIdp_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Payment account for given id does not exist: xyz");
        final OnTakeOfferParams params = getValidParams();
        params.paymentAccountId = "xyz";

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_offerIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Offer must not be null");
        final OnTakeOfferParams params = getValidParams();
        params.offer = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_currencyCodeIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("No such currency: null");
        final OnTakeOfferParams params = getValidParams();
        when(params.offer.getCurrencyCode()).thenReturn(null);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_currencyCodeReferencesNonExistingCurrency_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("No such currency: nonExistent");
        final OnTakeOfferParams params = getValidParams();
        when(params.offer.getCurrencyCode()).thenReturn("nonExistent");

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_currencyIsBanned_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        final String expectedMessage = Res.get("offerbook.warning.currencyBanned");
        Assert.assertNotNull(expectedMessage);
        expectedException.expectMessage(expectedMessage);
        final OnTakeOfferParams params = getValidParams();
        when(filterManager.isCurrencyBanned(params.offer.getCurrencyCode())).thenReturn(true);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_paymentMethodIsBanned_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        final String expectedMessage = Res.get("offerbook.warning.paymentMethodBanned");
        Assert.assertNotNull(expectedMessage);
        expectedException.expectMessage(expectedMessage);
        final OnTakeOfferParams params = getValidParams();
        when(filterManager.isPaymentMethodBanned(params.offer.getPaymentMethod())).thenReturn(true);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_offerIdIsBanned_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        final String expectedMessage = Res.get("offerbook.warning.offerBlocked");
        Assert.assertNotNull(expectedMessage);
        expectedException.expectMessage(expectedMessage);
        final OnTakeOfferParams params = getValidParams();
        when(filterManager.isOfferIdBanned(params.offer.getId())).thenReturn(true);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_makerNodeAddressIsBanned_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        final String expectedMessage = Res.get("offerbook.warning.nodeBlocked");
        Assert.assertNotNull(expectedMessage);
        expectedException.expectMessage(expectedMessage);
        final OnTakeOfferParams params = getValidParams();
        when(filterManager.isNodeAddressBanned(params.offer.getMakerNodeAddress())).thenReturn(true);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_amountIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Amount must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.amount = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_amountIsNegative_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Amount must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.amount = Coin.valueOf(-1);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_amountIsZero_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Amount must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.amount = Coin.ZERO;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_amountHigherThanOfferAmount_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Taken amount must not be higher than offer amount");
        final OnTakeOfferParams params = getValidParams();
        params.amount = params.offer.getAmount().add(Coin.SATOSHI);
        Assert.assertTrue(params.amount.isGreaterThan(params.offer.getAmount()));

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_txFeeIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Transaction fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.txFee = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_txFeeIsZero_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Transaction fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.txFee = Coin.ZERO;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_txFeeIsNegative_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Transaction fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.txFee = Coin.valueOf(-1);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_takerFeeIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Taker fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.takerFee = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_takerFeeIsZero_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Taker fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.takerFee = Coin.ZERO;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_takerFeeIsNegative_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Taker fee must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.takerFee = Coin.valueOf(-1);

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_tradePriceIsNegative_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Trade price must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.tradePrice = -1;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_tradePriceIsZero_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("Trade price must be a positive number");
        final OnTakeOfferParams params = getValidParams();
        params.tradePrice = 0;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_tradeResultHandlerIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("TradeResultHandler must not be null");
        final OnTakeOfferParams params = getValidParams();
        tradeResultHandlerMock = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_errorMessageHandlerIsNull_throwsException() {
//        Given
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("ErrorMessageHandler must not be null");
        final OnTakeOfferParams params = getValidParams();
        errorMessageHandlerMock = null;

//        When
        onTakeOffer(params);
    }

    @Test
    public void onTakeOffer_offerIsNotAvailable_callErrorHandler() {
//        Given
        final String errorMessage = "errorMessage" + new Date().toString();
        final OnTakeOfferParams params = getValidParams();
        doAnswer(invocation -> {
            final ErrorMessageHandler handler = invocation.getArgument(2);
            handler.handleErrorMessage(errorMessage);
            return null;
        }).when(params.offer).checkOfferAvailability(any(), any(), any());

//        When
        onTakeOffer(params);

//        Then
        verify(errorMessageHandlerMock).handleErrorMessage(errorMessage);
    }

    //    TODO do Tasks in BuyerAsTakerProtocol execute synchronously
    @Test
    public void onTakeOffer_firstAttemptToWriteATest() {
//        TODO why do we need to call this?
//        TODO why User is final? We cannot mock it if it's final.

//        Given
        doAnswer(invocation -> {
            ((ResultHandler) invocation.getArgument(1)).handleResult();
            return null;
        }).when(offerToCreate).checkOfferAvailability(any(), any(), any());

        doAnswer(invocation -> {
            Assert.assertNull("Trade should not have error property set", ((Trade) invocation.getArgument(0)).getErrorMessage());
            return null;
        }).when(tradeResultHandlerMock).handleResult(any());

//        When
        onTakeOffer(getValidParams());

//        Then
        verifyNoMoreInteractions(errorMessageHandlerMock);
//        TODO what properties should the trade have?
        verify(tradeResultHandlerMock).handleResult(any(Trade.class));
//        TODO what should happen as th e result of onTakeOffer call? What mocks should be called?
//        Following instructions can be used what calls are actually made
//        verifyNoMoreInteractions(p2pService);
//        verifyNoMoreInteractions(user);
//        verifyNoMoreInteractions(tradeStatisticsManager);
//        verifyNoMoreInteractions(arbitratorManager);
//        verifyNoMoreInteractions(btcWalletServiceMock);
//        verifyNoMoreInteractions(tradeWalletServiceMock);
    }

    private void onTakeOffer(@NotNull OnTakeOfferParams params) {
        getTradeManager().onTakeOffer(params.amount,
                params.txFee,
                params.takerFee,
                params.isCurrencyForTakerFeeBtc,
                params.tradePrice,
                params.fundsNeededForTrade,
                params.offer,
                params.paymentAccountId,
                params.useSavingsWallet, tradeResultHandlerMock, errorMessageHandlerMock);
    }

    @NotNull
    private TradeManager getTradeManager() {
        final TradeManager tradeManager = new TradeManager(user, keyRing, btcWalletService, bsqWalletService, tradeWalletService, openOfferManager, closedTradableManager, failedTradesManager, p2PService, priceFeedService, filterManager, tradeStatisticsManager, referralIdService, persistenceProtoResolver, accountAgeWitnessService, arbitratorManager, clock, storageDir);
        tradeManager.readPersisted();
        return tradeManager;
    }

    private OnTakeOfferParams getValidParams() {
        final OnTakeOfferParams params = new OnTakeOfferParams();
        params.amount = Coin.SATOSHI;
        params.txFee = Coin.SATOSHI;
        params.takerFee = Coin.SATOSHI;
        params.isCurrencyForTakerFeeBtc = true;
        params.tradePrice = 1;

        params.fundsNeededForTrade = Coin.ZERO;
        params.offer = offerToCreate;
        params.paymentAccountId = usersPaymentAccount.getId();
        params.useSavingsWallet = false;
        return params;
    }

    private Offer createOffer(String offerId, Arbitrator arbitrator, Mediator mediator) {
        final long now = new Date().getTime();
        final int price = 1;
        final double marketPriceMargin = 0.1;
        final boolean useMarketBasedPrice = false;
        final int amount = 1;
        final int minAmount = 1;
        final String baseCurrencyCode = "BTC";
        final String counterCurrencyCode = "USD";
        final long lastBlockSeenHeight = 1;
        final int txFee = 0;
        final int makerFee = 0;
        final boolean isCurrencyForMakerFeeBtc = false;
        final int buyerSecurityDeposit = 0;
        final int sellerSecurityDeposit = 0;
        final int maxTradeLimit = 0;
        final int maxTradePeriod = 0;
        final boolean useAutoClose = false;
        final boolean useReOpenAfterAutoClose = false;
        final int lowerClosePrice = 0;
        final int upperClosePrice = 0;
        final boolean isPrivateOffer = false;
        final String hashOfChallenge = null;
        final Map<String, String> extraDataMap = null;
        final KeyPair keyPair = Sig.generateKeyPair();
        final PubKeyRing pubKeyRing = new PubKeyRing(keyPair.getPublic(), keyPair.getPublic(), null);
        final List<NodeAddress> arbitrators = Collections.singletonList(arbitrator.getNodeAddress());
        final List<NodeAddress> mediators = Collections.singletonList(mediator.getNodeAddress());
        OfferPayload offerPayload = new OfferPayload(offerId,
                now,
                new NodeAddress("0", 0),
                pubKeyRing,
                OfferPayload.Direction.SELL,
                price,
                marketPriceMargin,
                useMarketBasedPrice,
                amount,
                minAmount,
                baseCurrencyCode,
                counterCurrencyCode,
                arbitrators,
                mediators,
                PaymentMethod.ALI_PAY_ID,
                "paymentAccountId",
                null,
                null,
                null,
                null,
                null,
                Version.VERSION,
                lastBlockSeenHeight,
                txFee,
                makerFee,
                isCurrencyForMakerFeeBtc,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                maxTradeLimit,
                maxTradePeriod,
                useAutoClose,
                useReOpenAfterAutoClose,
                lowerClosePrice,
                upperClosePrice,
                isPrivateOffer,
                hashOfChallenge,
                extraDataMap,
                Version.TRADE_PROTOCOL_VERSION);
        final Offer offer = new Offer(offerPayload);
        offer.setState(Offer.State.AVAILABLE);
        offer.setOfferFeePaymentTxId("abc");
//        TODO this is sick, offer should not include logic like checkOfferAvailability
        return Mockito.spy(offer);
    }

    private class OnTakeOfferParams {
        public Coin amount;
        public Coin txFee;
        public Coin takerFee;
        public boolean isCurrencyForTakerFeeBtc;
        public long tradePrice;
        public Coin fundsNeededForTrade;
        public Offer offer;
        public String paymentAccountId;
        public boolean useSavingsWallet;
    }
}
