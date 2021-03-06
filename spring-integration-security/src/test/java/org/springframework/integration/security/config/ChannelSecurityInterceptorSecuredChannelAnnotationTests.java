/*
 * Copyright 2014-2015 the original author or authors.
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

package org.springframework.integration.security.config;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.integration.annotation.BridgeTo;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.ChannelInterceptorAware;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.router.RecipientListRouter;
import org.springframework.integration.security.SecurityTestUtils;
import org.springframework.integration.security.TestHandler;
import org.springframework.integration.security.channel.ChannelSecurityInterceptor;
import org.springframework.integration.security.channel.SecuredChannel;
import org.springframework.integration.security.channel.SecurityContextPropagationChannelInterceptor;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Artem Bilan
 * @since 4.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChannelSecurityInterceptorSecuredChannelAnnotationTests {

	@Autowired
	MessageChannel securedChannel;

	@Autowired
	MessageChannel securedChannel2;

	@Autowired
	MessageChannel unsecuredChannel;

	@Autowired
	@Qualifier("queueChannel")
	MessageChannel queueChannel;

	@Autowired
	@Qualifier("securedChannelQueue")
	PollableChannel securedChannelQueue;

	@Autowired
	@Qualifier("executorChannel")
	MessageChannel executorChannel;

	@Autowired
	@Qualifier("errorChannel")
	PollableChannel errorChannel;

	@Autowired
	TestHandler testConsumer;


	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}


	@Test(expected = AccessDeniedException.class)
	public void testSecuredWithNotEnoughPermission() {
		login("bob", "bobspassword", "ROLE_ADMINA");
		securedChannel.send(new GenericMessage<String>("test"));
	}

	@Test
	public void testSecuredWithPermission() {
		login("bob", "bobspassword", "ROLE_ADMIN", "ROLE_PRESIDENT");
		securedChannel.send(new GenericMessage<String>("test"));
		securedChannel2.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 2, testConsumer.sentMessages.size());
	}

	@Test(expected = AccessDeniedException.class)
	public void testSecuredWithoutPermision() {
		login("bob", "bobspassword", "ROLE_USER");
		securedChannel.send(new GenericMessage<String>("test"));
	}

	@Test(expected = AccessDeniedException.class)
	public void testSecured2WithoutPermision() {
		login("bob", "bobspassword", "ROLE_USER");
		securedChannel2.send(new GenericMessage<String>("test"));
	}

	@Test(expected = AuthenticationException.class)
	public void testSecuredWithoutAuthenticating() {
		securedChannel.send(new GenericMessage<String>("test"));
	}

	@Test
	public void testUnsecuredAsAdmin() {
		login("bob", "bobspassword", "ROLE_ADMIN");
		unsecuredChannel.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}

	@Test
	public void testUnsecuredAsUser() {
		login("bob", "bobspassword", "ROLE_USER");
		unsecuredChannel.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}

	@Test
	public void testUnsecuredWithoutAuthenticating() {
		unsecuredChannel.send(new GenericMessage<String>("test"));
		assertEquals("Wrong size of message list in target", 1, testConsumer.sentMessages.size());
	}

	@Test
	public void testSecurityContextPropagationQueueChannel() {
		login("bob", "bobspassword", "ROLE_ADMIN", "ROLE_PRESIDENT");
		this.queueChannel.send(new GenericMessage<String>("test"));
		Message<?> receive = this.securedChannelQueue.receive(10000);
		assertNotNull(receive);

		SecurityContextHolder.clearContext();

		this.queueChannel.send(new GenericMessage<String>("test"));
		Message<?> errorMessage = this.errorChannel.receive(1000);
		assertNotNull(errorMessage);
		Object payload = errorMessage.getPayload();
		assertThat(payload, instanceOf(MessageHandlingException.class));
		assertThat(((MessageHandlingException) payload).getCause(),
				instanceOf(AuthenticationCredentialsNotFoundException.class));
	}

	@Test
	public void testSecurityContextPropagationExecutorChannel() {
		login("bob", "bobspassword", "ROLE_ADMIN", "ROLE_PRESIDENT");
		this.executorChannel.send(new GenericMessage<String>("test"));
		Message<?> receive = this.securedChannelQueue.receive(10000);
		assertNotNull(receive);

		SecurityContextHolder.clearContext();

		this.queueChannel.send(new GenericMessage<String>("test"));
		Message<?> errorMessage = this.errorChannel.receive(1000);
		assertNotNull(errorMessage);
		Object payload = errorMessage.getPayload();
		assertThat(payload, instanceOf(MessageHandlingException.class));
		assertThat(((MessageHandlingException) payload).getCause(),
				instanceOf(AuthenticationCredentialsNotFoundException.class));
	}


	private void login(String username, String password, String... roles) {
		SecurityContext context = SecurityTestUtils.createContext(username, password, roles);
		SecurityContextHolder.setContext(context);
	}


	@Configuration
	@EnableIntegration
	@ImportResource("classpath:org/springframework/integration/security/config/commonSecurityConfiguration.xml")
	public static class ContextConfiguration {

		@Bean
		@SecuredChannel(interceptor = "channelSecurityInterceptor", sendAccess = {"ROLE_ADMIN", "ROLE_PRESIDENT"})
		public SubscribableChannel securedChannel() {
			return new DirectChannel();
		}

		@Bean
		@SecuredChannel(interceptor = "channelSecurityInterceptor", sendAccess = {"ROLE_ADMIN", "ROLE_PRESIDENT"})
		public SubscribableChannel securedChannel2() {
			return new DirectChannel();
		}

		@Bean
		public SubscribableChannel unsecuredChannel() {
			return new DirectChannel();
		}

		@Bean
		@GlobalChannelInterceptor(patterns = {"queueChannel", "executorChannel"})
		public ChannelInterceptor securityContextPropagationInterceptor() {
			return new SecurityContextPropagationChannelInterceptor();
		}

		@Bean
		@BridgeTo(value = "securedChannelQueue", poller = @Poller(fixedDelay = "1000"))
		public PollableChannel queueChannel() {
			return new QueueChannel();
		}

		@Bean
		@SecuredChannel(interceptor = "channelSecurityInterceptor", sendAccess = {"ROLE_ADMIN", "ROLE_PRESIDENT"})
		public PollableChannel securedChannelQueue() {
			return new QueueChannel();
		}

		@Bean
		@BridgeTo("securedChannelQueue")
		public SubscribableChannel executorChannel() {
			return new ExecutorChannel(Executors.newSingleThreadExecutor());
		}


		@Bean
		public TaskScheduler taskScheduler() {
			return new ThreadPoolTaskScheduler();
		}

		@Bean
		public PollableChannel errorChannel() {
			return new QueueChannel();
		}

		@Bean
		public TestHandler testHandler() {
			TestHandler testHandler = new TestHandler();
			this.securedChannel().subscribe(testHandler);
			this.securedChannel2().subscribe(testHandler);
			this.unsecuredChannel().subscribe(testHandler);
			return testHandler;
		}

		@Bean
		public ChannelSecurityInterceptor channelSecurityInterceptor(AuthenticationManager authenticationManager,
				AccessDecisionManager accessDecisionManager) {
			ChannelSecurityInterceptor channelSecurityInterceptor = new ChannelSecurityInterceptor();
			channelSecurityInterceptor.setAuthenticationManager(authenticationManager);
			channelSecurityInterceptor.setAccessDecisionManager(accessDecisionManager);
			return channelSecurityInterceptor;
		}

	}

}
