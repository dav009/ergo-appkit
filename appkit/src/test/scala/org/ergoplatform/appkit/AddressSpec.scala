package org.ergoplatform.appkit

import org.ergoplatform.appkit.testing.AppkitTesting
import org.ergoplatform.{ErgoAddressEncoder, Pay2SAddress}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{PropSpec, Matchers}
import scorex.util.encode.Base16
import sigmastate.serialization.ErgoTreeSerializer



class AddressSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with AppkitTesting {

  def checkIsTestnetP2PKAddress(addr: Address) = {
    addr.isMainnet shouldBe false
    addr.isP2PK shouldBe true
    addr.getNetworkType shouldBe NetworkType.TESTNET
    addr.getErgoAddress.networkPrefix shouldBe ErgoAddressEncoder.TestnetNetworkPrefix
  }

  property("encoding vector") {
    val addr = Address.create(addrStr)
    addr.toString shouldBe addrStr
    checkIsTestnetP2PKAddress(addr)
  }

  property("Address fromMnemonic") {
    val addr = Address.fromMnemonic(NetworkType.TESTNET, mnemonic, SecretString.empty())
    addr.toString shouldBe addrStr
    checkIsTestnetP2PKAddress(addr)

    val addr2 = Address.fromMnemonic(NetworkType.MAINNET, mnemonic, SecretString.empty())
    addr2.toString shouldNot be (addrStr)

    val addr3 = Address.fromErgoTree(addr.getErgoAddress.script, NetworkType.TESTNET)
    addr3 shouldBe addr
  }

  property("Address from ErgoAddress") {
    implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder(ErgoAddressEncoder.TestnetNetworkPrefix);
    val ergoAddr = encoder.fromString(addrStr).get
    val addr = new Address(ergoAddr)
    checkIsTestnetP2PKAddress(addr)
    addr.toString shouldBe addrStr
  }

  property("construct P2S Address") {
    implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder(ErgoAddressEncoder.TestnetNetworkPrefix);
    val ergoAddr = encoder.fromString("Ms7smJwLGbUAjuWQ").get
    val addr = new Address(ergoAddr)
    addr.isP2S shouldBe true
    addr.getErgoAddress.script.constants.size shouldBe 1
  }

  property("construct P2SH Address") {
    implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder(ErgoAddressEncoder.TestnetNetworkPrefix);
    val ergoAddr = encoder.fromString("rbcrmKEYduUvADj9Ts3dSVSG27h54pgrq5fPuwB").get
    val addr = new Address(ergoAddr)
    addr.isP2S shouldBe false
    addr.isP2PK shouldBe false
  }

  property("Address createEip3Address") {
    val addr = Address.fromMnemonic(NetworkType.MAINNET, mnemonic, SecretString.empty())
    val firstEip3Addr = Address.createEip3Address(0, NetworkType.MAINNET, mnemonic, SecretString.empty())
    firstEip3Addr.toString shouldBe firstEip3AddrStr
    addr.toString shouldNot be (firstEip3AddrStr)

    val secondEip3Addr = Address.createEip3Address(1, NetworkType.MAINNET, mnemonic, SecretString.empty())
    secondEip3Addr.toString shouldBe secondEip3AddrStr
  }

  property("create Address from ErgoTree with DHT") {
    val tree = "100207036ba5cfbc03ea2471fdf02737f64dbcd58c34461a7ec1e586dcd713dacbf89a120400d805d601db6a01ddd6027300d603b2a5730100d604e4c672030407d605e4c672030507eb02ce7201720272047205ce7201720472027205"
    implicit val encoder: ErgoAddressEncoder = ErgoAddressEncoder.apply(NetworkType.MAINNET.networkPrefix);
    val ergoTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(Base16.decode(tree).get)
    val addr = Address.fromErgoTree(ergoTree, NetworkType.MAINNET).getErgoAddress
    val addr2 = encoder.fromProposition(ergoTree).get
    val addr3 = encoder.fromProposition(ergoTree.proposition).get
    addr shouldBe addr2
    addr shouldBe addr3
  }

  property("Address address from xpub key") {
    val xPubKey = Bip32Serialization.parseExtendedPublicKeyFromHex("0488b21e04220c2217000000009216e49a70865823eff5381d6fd33ac96743af1f3051dc4cc8edd66a29a740860326cfc301b0c8d4d815ac721e0551304417e6133c2c9137f9f22c33895a3e1650", NetworkType.MAINNET)

    val firstEip3Addr = Address.createEip3Address(0, NetworkType.MAINNET, xPubKey)
    firstEip3Addr.toString shouldBe "9hQ352ipFLWNA96FjCXPFidQrwp8gF4i9JUkrnxw6b4buVBFjVg"

    val secondEip3Addr = Address.createEip3Address(1, NetworkType.MAINNET, xPubKey)
    secondEip3Addr.toString shouldBe "9fzV11eLdVS1Mxzz59V7ewoar5FTLx7Eqfwh9XDfbL68DYTyfTv"
  }

}
