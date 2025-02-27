package org.ergoplatform.appkit;

import static com.google.common.base.Preconditions.checkState;
import static org.ergoplatform.appkit.BlockchainContext.DEFAULT_LIMIT_FOR_API;
import static org.ergoplatform.appkit.Parameters.MinFee;

import com.google.common.base.Preconditions;

import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.impl.ErgoTreeContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A collection of utility operations implemented in terms of abstract Appkit interfaces.
 */
public class BoxOperations {
    private final List<Address> senders;
    private final ErgoProver senderProver;
    private long amountToSpend = 0;
    private List<ErgoToken> tokensToSpend = Collections.emptyList();
    private long feeAmount = MinFee;
    private IUnspentBoxesLoader inputBoxesLoader = new ExplorerApiUnspentLoader();

    private BoxOperations(List<Address> senders, @Nullable ErgoProver senderProver) {
        this.senders = senders;
        this.senderProver = senderProver;
    }

    /**
     * Construct BoxOperations with a single sender address
     *
     * @param sender sender the following methods should use
     */
    public static BoxOperations createForSender(Address sender) {
        return createForSenders(Collections.singletonList(sender));
    }

    /**
     * Construct BoxOperations with a list of sender addresses
     *
     * @param senders list of senders the following methods should use
     */
    public static BoxOperations createForSenders(List<Address> senders) {
        return new BoxOperations(senders, null);
    }

    /**
     * Construct BoxOperations with a prover, deriving list of senders from prover from the
     * EIP-3 addresses.
     * All the derived EIP-3 addresses of the prover can be used to collect unspent boxes.
     */
    public static BoxOperations createForEip3Prover(ErgoProver senderProver) {
        List<Address> eip3Addresses = senderProver.getEip3Addresses();
        checkState(eip3Addresses.size() > 0,
            "EIP-3 addresses are not derived in the prover (use ErgoProverBuilder.withEip3Secret)");
        return new BoxOperations(eip3Addresses, senderProver);
    }

    /**
     * Construct BoxOperations with a prover, deriving list of senders from prover from either the
     * MASTER address of the given prover.
     */
    public static BoxOperations createForProver(ErgoProver senderProver) {
        return new BoxOperations(Collections.singletonList(senderProver.getAddress()), senderProver);
    }

    public BoxOperations withAmountToSpend(long amountToSpend) {
        if (amountToSpend < 0) {
            throw new IllegalArgumentException("Amount to send must be >= 0");
        }

        this.amountToSpend = amountToSpend;
        return this;
    }

    public BoxOperations withTokensToSpend(@Nonnull List<ErgoToken> tokensToSpend) {
        this.tokensToSpend = tokensToSpend;
        return this;
    }

    public BoxOperations withFeeAmount(long feeAmount) {
        if (feeAmount < MinFee) {
            throw new IllegalArgumentException("Amount to send must be >= " + MinFee);
        }

        this.feeAmount = feeAmount;
        return this;
    }

    /**
     * @param inputBoxesSource to use for {@link #getCoveringBoxesFor(long, List, Function)}
     *                         See {@link IUnspentBoxesLoader for more information}
     *                         Default is {@link ExplorerApiUnspentLoader}
     */
    public BoxOperations withInputBoxesLoader(@Nonnull IUnspentBoxesLoader inputBoxesSource) {
        this.inputBoxesLoader = inputBoxesSource;
        return this;
    }

    @Deprecated
    public static ErgoProver createProver(BlockchainContext ctx, Mnemonic mnemonic) {
        ErgoProver prover = ctx.newProverBuilder()
            .withMnemonic(mnemonic.getPhrase(), mnemonic.getPassword())
            .build();
        return prover;
    }

    @Deprecated
    public static ErgoProverBuilder createProver(BlockchainContext ctx, String storageFile, SecretString storagePass) {
        return createProver(ctx, storageFile, storagePass.toStringUnsecure());
    }

    @Deprecated
    public static ErgoProverBuilder createProver(
        BlockchainContext ctx, String storageFile, String storagePass) {
        SecretStorage storage = SecretStorage.loadFrom(storageFile);
        storage.unlock(storagePass);
        ErgoProverBuilder proverB = ctx.newProverBuilder()
            .withSecretStorage(storage);
        return proverB;
    }

    /**
     * Send the specified amountToSpend and tokens to the recipient.
     *
     * @param ctx       blockchain context obtained from {@link ErgoClient}
     * @param recipient the recipient address
     * @return json of the signed transaction
     */
    public String send(
        BlockchainContext ctx,
        Address recipient) {

        ErgoContract contract = new ErgoTreeContract(recipient.getErgoAddress().script());
        SignedTransaction signed = putToContractTx(ctx, contract);
        ctx.sendTransaction(signed);
        return signed.toJson(true);
    }

    /**
     * Load boxes for the given sender addresses covering the given amount of NanoErgs, fee and tokens.
     * The given page of boxes is loaded from each address and concatenated to a single
     * list.
     * The list is then used to select covering boxes.
     *
     * @param ctx the blockchain context to use for loading
     * @return a list of boxes covering the given amount
     */
    public List<InputBox> loadTop(BlockchainContext ctx) {
        List<InputBox> unspentBoxes = new ArrayList<>();
        long grossAmount = amountToSpend + feeAmount;
        long remainingAmount = grossAmount;
        SelectTokensHelper tokensHelper = new SelectTokensHelper(tokensToSpend);
        List<ErgoToken> remainingTokens = tokensToSpend;

        inputBoxesLoader.prepare(ctx, senders, grossAmount, tokensToSpend);

        for (Address sender : senders) {
            inputBoxesLoader.prepareForAddress(sender);
            CoveringBoxes unspent = getCoveringBoxesFor(remainingAmount, remainingTokens,
                page -> inputBoxesLoader.loadBoxesPage(ctx, sender, page));
            for (InputBox b : unspent.getBoxes()) {
                unspentBoxes.add(b);
                tokensHelper.foundNewTokens(b.getTokens());
                remainingAmount -= b.getValue();
                if (remainingAmount <= 0 && tokensHelper.areTokensCovered()) {
                    // collected enough boxes to cover the amount
                    break;
                }
            }
            if (remainingAmount <= 0 && tokensHelper.areTokensCovered()) break;
            remainingTokens = tokensHelper.getRemainingTokenList();
        }
        List<InputBox> selected = BoxSelectorsJavaHelpers.selectBoxes(unspentBoxes, grossAmount, tokensToSpend);
        return selected;
    }

    /**
     * Creates a new {@link SignedTransaction} which sends the given amount of NanoErgs and tokens
     * to the given contract.
     */
    public SignedTransaction putToContractTx(
        BlockchainContext ctx,
        ErgoContract contract) {
        if (senderProver == null) {
            throw new IllegalStateException("Call this only when prover is set");
        }

        UnsignedTransaction tx = putToContractTxUnsigned(ctx, contract);
        SignedTransaction signed = senderProver.sign(tx);
        return signed;
    }

    /**
     * Creates a new {@link UnsignedTransaction} which sends the given amount of NanoErgs and tokens
     * to the given contract.
     */
    public UnsignedTransaction putToContractTxUnsigned(
        BlockchainContext ctx,
        ErgoContract contract) {
        List<InputBox> boxesToSpend = loadTop(ctx);

        P2PKAddress changeAddress = senders.get(0).asP2PK();
        UnsignedTransactionBuilder txB = ctx.newTxBuilder();

        OutBoxBuilder outBoxBuilder = txB.outBoxBuilder()
            .value(amountToSpend)
            .contract(contract);
        if (!tokensToSpend.isEmpty())
            outBoxBuilder.tokens(tokensToSpend.toArray(new ErgoToken[]{}));
        OutBox newBox = outBoxBuilder.build();

        UnsignedTransaction tx = txB.boxesToSpend(boxesToSpend)
            .outputs(newBox)
            .fee(Parameters.MinFee)
            .sendChangeTo(changeAddress)
            .build();
        return tx;
    }

    public static SignedTransaction spendBoxesTx(
            BlockchainContext ctx,
            UnsignedTransactionBuilder txB,
            List<InputBox> boxes,
            ErgoProver sender, Address recipient, long amount, long fee) {
        OutBox newBox = txB.outBoxBuilder()
                .value(amount)
                .contract(new ErgoTreeContract(recipient.getErgoAddress().script()))
                .build();

        UnsignedTransaction tx = txB.boxesToSpend(boxes)
                .outputs(newBox)
                .fee(fee)
                .sendChangeTo(sender.getP2PKAddress())
                .build();
        SignedTransaction signed = sender.sign(tx);
        return signed;
    }

    /**
     * Get unspent boxes from a paged boxes source and selects the top ones needed to spent
     * to satisfy amountToSpend and tokensToSpend.
     * <p>
     * inputBoxesLoader must satisfy the following needs:
     * - receives a 0-based integer, the page that should be loaded
     * - returns a list of {@link InputBox} to select from. First items are preferred to be selected
     * - must not return null
     * - returning an empty list means the source of input boxes is drained and no further page will
     *   be loaded
     *
     * @param amountToSpend    amount of NanoErgs to be covered
     * @param tokensToSpend    ErgoToken to spent
     * @param inputBoxesLoader method returning paged sets of InputBoxes, see above
     * @return a new instance of {@link CoveringBoxes} set
     */
    public static CoveringBoxes getCoveringBoxesFor(long amountToSpend,
                                                    List<ErgoToken> tokensToSpend,
                                                    Function<Integer, List<InputBox>> inputBoxesLoader) {
        SelectTokensHelper tokensRemaining = new SelectTokensHelper(tokensToSpend);
        Preconditions.checkArgument(amountToSpend > 0 ||
            !tokensRemaining.areTokensCovered(), "amountToSpend or tokens to spend should be > 0");
        ArrayList<InputBox> selectedCoveringBoxes = new ArrayList<>();
        long remainingAmountToCover = amountToSpend;
        int page = 0;
        while (true) {
            List<InputBox> chunk = inputBoxesLoader.apply(page);
            for (InputBox boxCandidate : chunk) {
                // on rare occasions, chunk can include entries that we already had received on a
                // previous chunk page. We make sure we don't add any duplicate entries.
                if (!isAlreadyAdded(selectedCoveringBoxes, boxCandidate)) {
                    boolean usefulTokens = tokensRemaining.foundNewTokens(boxCandidate.getTokens());
                    if (usefulTokens || remainingAmountToCover > 0) {
                        selectedCoveringBoxes.add(boxCandidate);
                        remainingAmountToCover -= boxCandidate.getValue();
                    }
                    if (remainingAmountToCover <= 0 && tokensRemaining.areTokensCovered())
                        return new CoveringBoxes(amountToSpend, selectedCoveringBoxes);
                }
            }
            // this chunk is not enough, go to the next (if any)
            if (chunk.size() == 0) {
                // this was the last chunk, but still remain to collect
                assert remainingAmountToCover > 0 || !tokensRemaining.areTokensCovered();
                // cannot satisfy the request, but still return cb, with cb.isCovered == false
                return new CoveringBoxes(amountToSpend, selectedCoveringBoxes);
            }
            // step to next chunk
            page++;
        }
    }

    /**
     * @return true when boxCandidate is already added to selectedBoxes list
     */
    private static boolean isAlreadyAdded(ArrayList<InputBox> selectedBoxes, InputBox boxCandidate) {
        boolean alreadyAdded = false;
        for (InputBox coveringBox : selectedBoxes) {
            if (coveringBox.getId().equals(boxCandidate.getId())) {
                alreadyAdded = true;
                break;
            }
        }
        return alreadyAdded;
    }

    /**
     * Use this interface to adapt behaviour of unspent boxes loading.
     */
    public interface IUnspentBoxesLoader {
        /**
         * Called before first call to {@link #loadBoxesPage(BlockchainContext, Address, Integer)} is done
         * Called before first call to {@link #prepareForAddress(Address)}
         *
         * @param ctx           {@link BlockchainContext} to work with, if needed
         * @param addresses     addresses boxes will be fetched for
         * @param grossAmount   overall amount of nanoergs needed to satisfy the caller
         * @param tokensToSpend overall amount of tokens needed to satisfy the caller
         */
        void prepare(@Nonnull BlockchainContext ctx, List<Address> addresses, long grossAmount,
                     @Nonnull List<ErgoToken> tokensToSpend);

        /**
         * Called before first call to {@link #loadBoxesPage(BlockchainContext, Address, Integer)}
         * for a single address.
         *
         * @param address address that will be fetched next
         */
        void prepareForAddress(Address address);

        /**
         * @param ctx     {@link BlockchainContext} to work with, if needed
         * @param address p2pk address unspent boxes list should be fetched for
         * @param integer page that should be loaded, 0-based integer
         * @return a list of InputBox to select from. First items are preferred to be selected.
         * Returning an empty list means the source of input boxes is drained and no further
         * page should be loaded
         */
        @Nonnull
        List<InputBox> loadBoxesPage(@Nonnull BlockchainContext ctx, @Nonnull Address address, @Nonnull Integer integer);
    }

    /**
     * Adds a checker method to {@link ExplorerApiUnspentLoader} for boxes that should be omitted
     * when loading boxes from Explorer API
     */
    public abstract static class ExplorerApiWithCheckerLoader extends ExplorerApiUnspentLoader {
        protected abstract boolean canUseBox(InputBox box);

        @Override
        @Nonnull
        public List<InputBox> loadBoxesPage(@Nonnull BlockchainContext ctx, @Nonnull Address sender, @Nonnull Integer page) {
            List<InputBox> boxes = super.loadBoxesPage(ctx, sender, page);

            List<InputBox> returnedBoxes = new ArrayList<>(boxes.size());
            int pageOffset = 0;
            while (!boxes.isEmpty() && returnedBoxes.isEmpty()) {
                for (InputBox boxToCheck : boxes) {
                    if (canUseBox(boxToCheck))
                        returnedBoxes.add(boxToCheck);
                }

                // edge case: we had boxes loaded from Explorer, but all were blacklisted...
                // load next page!
                // This will make return the same set on next call by getCoveringBoxes, but
                // getCoveringBoxes handles duplicates so we just have a call too often
                if (returnedBoxes.isEmpty()) {
                    pageOffset++;
                    boxes = super.loadBoxesPage(ctx, sender, page + pageOffset);
                }
            }
            return returnedBoxes;
        }
    }

    /**
     * Default loader for unspent boxes. Loads unspent boxes for an address directly from Explorer API
     */
    public static class ExplorerApiUnspentLoader implements IUnspentBoxesLoader {
        @Override
        public void prepare(@Nonnull BlockchainContext ctx, List<Address> addresses, long grossAmount, @Nonnull List<ErgoToken> tokensToSpend) {
            // not needed
        }

        @Override
        public void prepareForAddress(Address address) {
            // not needed
        }

        @Override
        @Nonnull
        public List<InputBox> loadBoxesPage(@Nonnull BlockchainContext ctx, @Nonnull Address address, @Nonnull Integer page) {
            return ctx.getUnspentBoxesFor(address, page * DEFAULT_LIMIT_FOR_API, DEFAULT_LIMIT_FOR_API);
        }
    }
}
