/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ws;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Element;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.transformer.ExpressionEvaluatingTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointMapping;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.mapping.UriEndpointMapping;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.server.SoapMessageDispatcher;
import org.springframework.ws.transport.WebServiceMessageReceiver;
import org.springframework.xml.transform.StringSource;

/**
 * @author Artem Bilan
 * @since 4.3
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class WebServiceInboundGatewayJavaConfigTests {

	@Autowired
	private WebServiceMessageReceiver messageReceiver;

	@Autowired
	private PollableChannel webserviceRequestsQueue;

	@Test
	public void testWebServiceInboundGatewayJavaConfig() throws Exception {
		MessageContext context = mock(MessageContext.class);
		SoapMessage request = mock(SoapMessage.class);
		SoapMessage response = mock(SoapMessage.class);

		String input = "<hello/>";
		Source payloadSource = new StringSource(input);
		StringWriter output = new StringWriter();
		Result payloadResult = new StreamResult(output);

		when(context.getResponse()).thenReturn(response);
		when(response.getPayloadResult()).thenReturn(payloadResult);
		when(context.getRequest()).thenReturn(request);
		when(request.getPayloadSource()).thenReturn(payloadSource);

		this.messageReceiver.receive(context);

		assertTrue(output.toString().endsWith(input));


		context = mock(MessageContext.class);
		request = mock(SoapMessage.class);

		payloadSource = new StringSource("<order/>");

		when(context.getRequest()).thenReturn(request);
		when(request.getPayloadSource()).thenReturn(payloadSource);

		this.messageReceiver.receive(context);

		Message<?> receive = this.webserviceRequestsQueue.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(Element.class));
		Element order = (Element) receive.getPayload();
		assertEquals("order", order.getLocalName());
	}

	@Configuration
	@EnableIntegration
	@EnableWs
	@IntegrationComponentScan
	public static class ContextConfiguration {

		@Bean
		public WebServiceMessageReceiver messageDispatcher() {
			return new SoapMessageDispatcher();
		}

		@Bean
		public EndpointMapping uriEndpointMapping() {
			UriEndpointMapping endpointMapping = new UriEndpointMapping();
			endpointMapping.setDefaultEndpoint(wsGateway());
			return endpointMapping;
		}

		@Bean
		public MessageEndpoint wsGateway() {
			SimpleWebServiceInboundGateway gateway = new SimpleWebServiceInboundGateway();
			gateway.setRequestChannel(gatewayRequests());
			return gateway;
		}

		@Bean
		public MessageChannel gatewayRequests() {
			return new DirectChannel();
		}

		@Bean
		@Transformer(inputChannel = "gatewayRequests")
		public ExpressionEvaluatingTransformer transformer() {
			return new ExpressionEvaluatingTransformer(new SpelExpressionParser().parseExpression("payload.toString()"));
		}

		@Bean
		public PollableChannel webserviceRequestsQueue() {
			return new QueueChannel();
		}

	}

	@Endpoint
	@MessagingGateway(defaultRequestChannel = "webserviceRequestsQueue")
	interface WebService {

		@PayloadRoot(localPart = "order")
		void order(@RequestPayload Element orderElement);

	}

}
