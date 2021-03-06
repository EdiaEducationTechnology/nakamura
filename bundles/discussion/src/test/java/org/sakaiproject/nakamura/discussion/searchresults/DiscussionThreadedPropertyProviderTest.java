/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.discussion.searchresults;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.message.MessageConstants;

import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class DiscussionThreadedPropertyProviderTest {
  DiscussionThreadedPropertyProvider discussionThreadedPropertyProvider;

  @Mock
  SlingHttpServletRequest request;
  @Mock
  Map<String, String> propertiesMap;

  /**
   * @throws java.lang.Exception
   */
  @Before
  public void setUp() throws Exception {
    discussionThreadedPropertyProvider = new DiscussionThreadedPropertyProvider();
  }

  /**
   * KERN-2901 Able to create NullPointerException anonymously by visiting
   * /var/search/discussions/threaded.json.inc
   */
  @Test
  public void testLoadUserPropertiesNullPath() {
    when(request.getParameter("path")).thenReturn(null);
    discussionThreadedPropertyProvider.loadUserProperties(request, propertiesMap);
    verify(propertiesMap, never()).put(eq(MessageConstants.SEARCH_PROP_MESSAGEROOT),
        anyString());
  }

}
