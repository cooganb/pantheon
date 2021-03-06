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
package tech.pegasys.pantheon.ethereum.p2p.testing;

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link MockNetwork}. */
public final class MockNetworkTest {

  @Test
  public void exchangeMessages() throws Exception {
    final Capability cap = Capability.create("eth", 63);
    final MockNetwork network = new MockNetwork(Arrays.asList(cap));
    final Peer one = new DefaultPeer(randomId(), "192.168.1.2", 1234, 4321);
    final Peer two = new DefaultPeer(randomId(), "192.168.1.3", 1234, 4321);
    try (final P2PNetwork network1 = network.setup(one);
        final P2PNetwork network2 = network.setup(two)) {
      final CompletableFuture<Message> messageFuture = new CompletableFuture<>();
      network1.subscribe(cap, messageFuture::complete);
      final Predicate<PeerConnection> isPeerOne =
          peerConnection -> peerConnection.getPeer().getNodeId().equals(one.getId());
      final Predicate<PeerConnection> isPeerTwo =
          peerConnection -> peerConnection.getPeer().getNodeId().equals(two.getId());
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst())
          .isNotPresent();
      Assertions.assertThat(network2.getPeers().stream().filter(isPeerOne).findFirst())
          .isNotPresent();

      // Validate Connect Behaviour
      final CompletableFuture<PeerConnection> peer2Future = new CompletableFuture<>();
      network1.subscribeConnect(peer2Future::complete);
      final CompletableFuture<PeerConnection> peer1Future = new CompletableFuture<>();
      network2.subscribeConnect(peer1Future::complete);
      network1.connect(two).get();
      Assertions.assertThat(peer1Future.get().getPeer().getNodeId()).isEqualTo(one.getId());
      Assertions.assertThat(peer2Future.get().getPeer().getNodeId()).isEqualTo(two.getId());
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst()).isPresent();
      final Optional<PeerConnection> optionalConnection =
          network2.getPeers().stream().filter(isPeerOne).findFirst();
      Assertions.assertThat(optionalConnection).isPresent();

      // Validate Message Exchange
      final int size = 128;
      final ByteBuf dataSent = NetworkMemoryPool.allocate(size);
      final byte[] data = new byte[size];
      ThreadLocalRandom.current().nextBytes(data);
      dataSent.writeBytes(data);
      final int code = 0x74;
      final PeerConnection connection = optionalConnection.get();
      connection.send(cap, new RawMessage(code, dataSent));
      final Message receivedMessage = messageFuture.get();
      final MessageData receivedMessageData = receivedMessage.getData();
      final ByteBuf receiveBuffer = NetworkMemoryPool.allocate(size);
      receivedMessageData.writeTo(receiveBuffer);
      Assertions.assertThat(receiveBuffer.compareTo(dataSent)).isEqualTo(0);
      Assertions.assertThat(receivedMessage.getConnection().getPeer().getNodeId())
          .isEqualTo(two.getId());
      Assertions.assertThat(receivedMessageData.getSize()).isEqualTo(size);
      Assertions.assertThat(receivedMessageData.getCode()).isEqualTo(code);

      // Validate Disconnect Behaviour
      final CompletableFuture<DisconnectReason> peer1DisconnectFuture = new CompletableFuture<>();
      final CompletableFuture<DisconnectReason> peer2DisconnectFuture = new CompletableFuture<>();
      network2.subscribeDisconnect(
          (peer, reason, initiatedByPeer) -> peer1DisconnectFuture.complete(reason));
      network1.subscribeDisconnect(
          (peer, reason, initiatedByPeer) -> peer2DisconnectFuture.complete(reason));
      connection.disconnect(DisconnectReason.CLIENT_QUITTING);
      Assertions.assertThat(peer1DisconnectFuture.get()).isEqualTo(DisconnectReason.REQUESTED);
      Assertions.assertThat(peer2DisconnectFuture.get())
          .isEqualTo(DisconnectReason.CLIENT_QUITTING);
      Assertions.assertThat(network1.getPeers().stream().filter(isPeerTwo).findFirst())
          .isNotPresent();
      Assertions.assertThat(network2.getPeers().stream().filter(isPeerOne).findFirst())
          .isNotPresent();
    }
  }

  private static BytesValue randomId() {
    final byte[] raw = new byte[DefaultPeer.PEER_ID_SIZE];
    ThreadLocalRandom.current().nextBytes(raw);
    return BytesValue.wrap(raw);
  }
}
