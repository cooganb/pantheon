/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;

public class IbftHelpers {

  public static final Hash EXPECTED_MIX_HASH =
      Hash.fromHexString("0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365");

  public static int calculateRequiredValidatorQuorum(final int validatorCount) {
    return Util.fastDivCeiling(2 * validatorCount, 3);
  }
}
