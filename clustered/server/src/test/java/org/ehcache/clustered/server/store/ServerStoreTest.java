/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehcache.clustered.server.store;


import org.ehcache.clustered.common.store.Chain;
import org.ehcache.clustered.common.store.Element;
import org.ehcache.clustered.common.store.ServerStore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Verify Server Store
 */
public abstract class ServerStoreTest {

  public abstract ServerStore newStore();

  public abstract ChainBuilder newChainBuilder();

  public abstract ElementBuilder newElementBuilder();

  private final ChainBuilder chainBuilder = newChainBuilder();
  private final ElementBuilder elementBuilder = newElementBuilder();

  private static void populateStore(ServerStore store) {
    for(int i = 1 ; i <= 16; i++) {
      store.append(i, createPayload(i));
    }
  }

  private static long readPayLoad(ByteBuffer byteBuffer) {
    return byteBuffer.getLong();
  }

  private static ByteBuffer createPayload(long key) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(8).putLong(key);
    byteBuffer.flip();
    return byteBuffer;
  }

  private static void assertChainAndReverseChainOnlyHave(Chain chain, long... payLoads) {
    Iterator<Element> elements = chain.iterator();
    for (long payLoad : payLoads) {
      assertThat(readPayLoad(elements.next().getPayload()), is(Long.valueOf(payLoad)));
    }
    assertThat(elements.hasNext(), is(false));

    Iterator<Element> reverseElements = chain.reverseIterator();

    for (int i = payLoads.length -1; i >= 0; i--) {
      assertThat(readPayLoad(reverseElements.next().getPayload()), is(Long.valueOf(payLoads[i])));
    }
    assertThat(reverseElements.hasNext(), is(false));
  }

  @Test
  public void testGetNoMappingExists() {
    ServerStore store = newStore();
    Chain chain = store.get(1);
    assertThat(chain.isEmpty(), is(true));
    assertThat(chain.iterator().hasNext(), is(false));
  }

  @Test
  public void testGetMappingExists() {
    ServerStore store = newStore();
    populateStore(store);
    Chain chain = store.get(1);
    assertThat(chain.isEmpty(), is(false));
    assertChainAndReverseChainOnlyHave(chain, 1);
  }

  @Test
  public void testAppendNoMappingExists() {
    ServerStore store = newStore();
    store.append(1, createPayload(1));
    Chain chain = store.get(1);
    assertThat(chain.isEmpty(), is(false));
    assertChainAndReverseChainOnlyHave(chain, 1);
  }

  @Test
  public void testAppendMappingExists() {
    ServerStore store = newStore();
    populateStore(store);
    store.append(2, createPayload(22));
    Chain chain = store.get(2);
    assertThat(chain.isEmpty(), is(false));
    assertChainAndReverseChainOnlyHave(chain, 2, 22);
  }

  @Test
  public void testGetAndAppendNoMappingExists() {
    ServerStore store = newStore();
    Chain chain = store.getAndAppend(1, createPayload(1));
    assertThat(chain.isEmpty(), is(true));
    chain = store.get(1);
    assertChainAndReverseChainOnlyHave(chain, 1);
  }

  @Test
  public void testGetAndAppendMappingExists() {
    ServerStore store = newStore();
    populateStore(store);
    Chain chain = store.getAndAppend(1, createPayload(22));
    for (Element element : chain) {
      assertThat(readPayLoad(element.getPayload()), is(Long.valueOf(1)));
    }
    chain = store.get(1);
    assertChainAndReverseChainOnlyHave(chain, 1, 22);
  }

  @Test
  public void testReplaceAtHeadSucceedsMappingExistsHeadMatchesStrictly() {
    ServerStore store = newStore();
    populateStore(store);
    Chain existingMapping = store.get(1);

    store.replaceAtHead(1, existingMapping, chainBuilder.build(elementBuilder.build(createPayload(11))));
    Chain chain = store.get(1);
    assertChainAndReverseChainOnlyHave(chain, 11);

    store.append(2, createPayload(22));
    store.append(2, createPayload(222));

    existingMapping = store.get(2);

    store.replaceAtHead(2, existingMapping, chainBuilder.build(elementBuilder.build(createPayload(2222))));

    chain = store.get(2);

    assertChainAndReverseChainOnlyHave(chain, 2222);
  }

  @Test
  public void testReplaceAtHeadSucceedsMappingExistsHeadMatches() {
    ServerStore store = newStore();
    populateStore(store);

    Chain existingMapping = store.get(1);

    store.append(1, createPayload(11));

    store.replaceAtHead(1, existingMapping, chainBuilder.build(elementBuilder.build(createPayload(111))));
    Chain chain = store.get(1);

    assertChainAndReverseChainOnlyHave(chain, 111, 11);

    store.append(2, createPayload(22));
    existingMapping = store.get(2);

    store.append(2, createPayload(222));

    store.replaceAtHead(2, existingMapping, chainBuilder.build(elementBuilder.build(createPayload(2222))));

    chain = store.get(2);
    assertChainAndReverseChainOnlyHave(chain, 2222, 222);
  }

  @Test
  public void testReplaceAtHeadIgnoredMappingExistsHeadMisMatch() {
    ServerStore store = newStore();
    populateStore(store);

    store.append(1, createPayload(11));
    store.append(1, createPayload(111));

    Chain mappingReadFirst = store.get(1);
    store.replaceAtHead(1, mappingReadFirst, chainBuilder.build(elementBuilder.build(createPayload(111))));

    Chain current = store.get(1);
    assertChainAndReverseChainOnlyHave(current, 111);

    store.append(1, createPayload(1111));
    store.replaceAtHead(1, mappingReadFirst, chainBuilder.build(elementBuilder.build(createPayload(11111))));

    Chain toVerify = store.get(1);

    assertChainAndReverseChainOnlyHave(toVerify, 111, 1111);
  }

}
