package org.ergoplatform.appkit

import java.util

import org.ergoplatform.appkit.Mnemonic._
import org.ergoplatform.appkit.MnemonicValidationException.{MnemonicChecksumException, MnemonicEmptyException, MnemonicWordException, MnemonicWrongListSizeException}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{Matchers, PropSpec}

import scala.collection.JavaConversions.seqAsJavaList

class MnemonicSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  val testMnemonic = "walnut endorse maid alone fuel jump torch company ahead nice abstract earth pig spice reduce"
  val testEntropy: Array[Byte] = Array[Byte](-10, -55, 58, 24, 3, 117, -34, -15, -7, 81, 116, 5, 50, -84, 3, -94, -70, 73, -93, 45)

  property("generate") {
    val mnemonic = Mnemonic.generate(LANGUAGE_ID_ENGLISH, DEFAULT_STRENGTH, testEntropy)
    mnemonic shouldBe testMnemonic
  }

  property("entropy") {
    val entropy = getEntropy(DEFAULT_STRENGTH)
    entropy.length shouldBe 20
  }

  property("checkMnemonic") {
    val entropy = Mnemonic.toEntropy(LANGUAGE_ID_ENGLISH, seqAsJavaList(testMnemonic.split(' ')))
    entropy shouldBe testEntropy

    an[MnemonicWordException] should be thrownBy Mnemonic.checkEnglishMnemonic(
      seqAsJavaList("wanut endorse maid alone fuel jump torch company ahead nice abstract earth pig spice reduce".split(' ')))
    an[MnemonicWrongListSizeException] should be thrownBy Mnemonic.checkEnglishMnemonic(
      seqAsJavaList("walnut endorse maid alone fuel jump torch company ahead nice abstract earth pig spice".split(' ')))
    an[MnemonicChecksumException] should be thrownBy Mnemonic.checkEnglishMnemonic(
      seqAsJavaList("walnut endorse maid alone fuel jump torch company ahead nice abstract earth pig spice earth".split(' ')))
    an[MnemonicEmptyException] should be thrownBy Mnemonic.checkEnglishMnemonic(new util.ArrayList[String]())
  }
}

