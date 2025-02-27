package org.ergoplatform.appkit.impl;

import org.ergoplatform.ErgoLikeTransaction;
import org.ergoplatform.ErgoLikeTransactionSerializer$;
import org.ergoplatform.appkit.*;
import org.ergoplatform.restapi.client.ApiClient;
import org.ergoplatform.restapi.client.NodeInfo;
import sigmastate.Values;
import sigmastate.serialization.SigmaSerializer$;
import sigmastate.utils.SigmaByteReader;

public abstract class BlockchainContextBase implements BlockchainContext {
    protected final NetworkType _networkType;

    public BlockchainContextBase(NetworkType networkType) {
        _networkType = networkType;
    }

    @Override
    public ErgoContract newContract(Values.ErgoTree ergoTree) {
        return new ErgoTreeContract(ergoTree);
    }

    @Override
    public ErgoContract compileContract(Constants constants, String ergoScript) {
        return ErgoScriptContract.create(constants, ergoScript, _networkType);
    }

    @Override
    public NetworkType getNetworkType() {
        return _networkType;
    }

    abstract ApiClient getApiClient();

    public abstract NodeInfo getNodeInfo();

    @Override
    public ReducedTransaction parseReducedTransaction(byte[] txBytes) {
        SigmaByteReader r = SigmaSerializer$.MODULE$.startReader(txBytes, 0);
        ReducedErgoLikeTransaction tx = ReducedErgoLikeTransactionSerializer$.MODULE$.parse(r);
        int cost = (int)r.getUInt(); // TODO use java7.compat.Math.toIntExact when it will available in Sigma
        return new ReducedTransactionImpl(this, tx, cost);
    }

    @Override
    public SignedTransaction parseSignedTransaction(byte[] txBytes) {
        SigmaByteReader r = SigmaSerializer$.MODULE$.startReader(txBytes, 0);
        ErgoLikeTransaction tx = ErgoLikeTransactionSerializer$.MODULE$.parse(r);
        int cost = (int)r.getUInt(); // TODO use java7.compat.Math.toIntExact when it will available in Sigma
        return new SignedTransactionImpl(this, tx, cost);
    }
}
