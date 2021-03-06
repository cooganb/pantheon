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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class AdminJsonRpcHttpServiceTest extends JsonRpcHttpServiceTest {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void getPeers() throws Exception {
    final List<Capability> caps = new ArrayList<>();
    caps.add(Capability.create("eth", 61));
    caps.add(Capability.create("eth", 62));
    final List<PeerConnection> peerList = new ArrayList<>();
    final PeerInfo info1 =
        new PeerInfo(4, CLIENT_VERSION, caps, 30302, BytesValue.fromHexString("0001"));
    final PeerInfo info2 =
        new PeerInfo(4, CLIENT_VERSION, caps, 60302, BytesValue.fromHexString("0002"));
    final PeerInfo info3 =
        new PeerInfo(4, CLIENT_VERSION, caps, 60303, BytesValue.fromHexString("0003"));
    final InetSocketAddress addr30301 = new InetSocketAddress("localhost", 30301);
    final InetSocketAddress addr30302 = new InetSocketAddress("localhost", 30302);
    final InetSocketAddress addr30303 = new InetSocketAddress("localhost", 30303);
    final InetSocketAddress addr60301 = new InetSocketAddress("localhost", 60301);
    final InetSocketAddress addr60302 = new InetSocketAddress("localhost", 60302);
    final InetSocketAddress addr60303 = new InetSocketAddress("localhost", 60303);

    peerList.add(new MockPeerConnection(info1, addr60301, addr30302));
    peerList.add(new MockPeerConnection(info2, addr30301, addr60302));
    peerList.add(new MockPeerConnection(info3, addr30301, addr60303));

    when(peerDiscoveryMock.getPeers()).thenReturn(peerList);

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"admin_peers\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl).build();
    LOG.info("Request: " + request);
    try (final Response resp = client.newCall(request).execute()) {
      LOG.info("Response: " + resp);

      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      LOG.info("Response Body: " + json.encodePrettily());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonArray result = json.getJsonArray("result");

      assertPeerResultMatchesPeer(result, peerList);
    }
  }

  protected void assertPeerResultMatchesPeer(
      final JsonArray result, final Collection<PeerConnection> peerList) {
    int i = -1;
    for (final PeerConnection peerConn : peerList) {
      final JsonObject peerJson = result.getJsonObject(++i);
      final int jsonVersion = Integer.decode(peerJson.getString("version"));
      final String jsonClient = peerJson.getString("name");
      final List<Capability> caps = getCapabilities(peerJson.getJsonArray("caps"));
      final int jsonPort = Integer.decode(peerJson.getString("port"));
      final BytesValue jsonNodeId = BytesValue.fromHexString(peerJson.getString("id"));

      final PeerInfo jsonPeer = new PeerInfo(jsonVersion, jsonClient, caps, jsonPort, jsonNodeId);
      assertThat(peerConn.getPeer()).isEqualTo(jsonPeer);
    }
  }

  protected List<Capability> getCapabilities(final JsonArray jsonCaps) {
    final List<Capability> caps = new ArrayList<>();
    for (final Object jsonCap : jsonCaps) {
      final StringTokenizer st = new StringTokenizer(jsonCap.toString(), "/");
      caps.add(Capability.create(st.nextToken(), Integer.valueOf(st.nextToken())));
    }
    return caps;
  }
}
