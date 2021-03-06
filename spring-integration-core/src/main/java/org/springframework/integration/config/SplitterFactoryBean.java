/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.integration.config;

import org.springframework.expression.Expression;
import org.springframework.integration.handler.AbstractMessageProducingHandler;
import org.springframework.integration.handler.AbstractReplyProducingMessageHandler;
import org.springframework.integration.splitter.AbstractMessageSplitter;
import org.springframework.integration.splitter.DefaultMessageSplitter;
import org.springframework.integration.splitter.ExpressionEvaluatingSplitter;
import org.springframework.integration.splitter.MethodInvokingSplitter;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Factory bean for creating a Message Splitter.
 *
 * @author Mark Fisher
 * @author Iwein Fuld
 * @author Gary Russell
 * @author David Liu
 */
public class SplitterFactoryBean extends AbstractStandardMessageHandlerFactoryBean {

	private volatile Long sendTimeout;

	private volatile Boolean requiresReply;

	private volatile Boolean applySequence;

	private volatile String delimiters;


	public void setSendTimeout(Long sendTimeout) {
		this.sendTimeout = sendTimeout;
	}

	public boolean isRequiresReply() {
		return requiresReply;
	}

	public void setRequiresReply(boolean requiresReply) {
		this.requiresReply = requiresReply;
	}

	public void setApplySequence(boolean applySequence) {
		this.applySequence = applySequence;
	}

	public void setDelimiters(String delimiters) {
		this.delimiters = delimiters;
	}

	@Override
	protected MessageHandler createMethodInvokingHandler(Object targetObject, String targetMethodName) {
		Assert.notNull(targetObject, "targetObject must not be null");
		AbstractMessageSplitter splitter = this.extractTypeIfPossible(targetObject, AbstractMessageSplitter.class);
		if (splitter == null) {
			this.checkForIllegalTarget(targetObject, targetMethodName);
			splitter = this.createMethodInvokingSplitter(targetObject, targetMethodName);
			this.configureSplitter(splitter);
		}
		else {
			Assert.isTrue(!StringUtils.hasText(targetMethodName), "target method should not be provided when the target "
					+ "object is an implementation of AbstractMessageSplitter");
			this.configureSplitter(splitter);
			if (targetObject instanceof MessageHandler) {
				return (MessageHandler) targetObject;
			}
		}
		return splitter;
	}

	protected AbstractMessageSplitter createMethodInvokingSplitter(Object targetObject, String targetMethodName) {
		return (StringUtils.hasText(targetMethodName))
				? new MethodInvokingSplitter(targetObject, targetMethodName)
				: new MethodInvokingSplitter(targetObject);
	}

	@Override
	protected MessageHandler createExpressionEvaluatingHandler(Expression expression) {
		return this.configureSplitter(new ExpressionEvaluatingSplitter(expression));
	}

	@Override
	protected MessageHandler createDefaultHandler() {
		return this.configureSplitter(new DefaultMessageSplitter());
	}

	protected AbstractMessageSplitter configureSplitter(AbstractMessageSplitter splitter) {
		this.postProcessReplyProducer(splitter);
		return splitter;
	}

	@Override
	protected boolean canBeUsedDirect(AbstractMessageProducingHandler handler) {
		return handler instanceof AbstractMessageSplitter
				|| (this.applySequence == null && this.delimiters == null);
	}

	@Override
	protected void postProcessReplyProducer(AbstractMessageProducingHandler handler) {
		if (this.sendTimeout != null) {
			handler.setSendTimeout(sendTimeout);
		}
		if (this.requiresReply != null) {
			if(handler instanceof AbstractReplyProducingMessageHandler) {
				((AbstractReplyProducingMessageHandler) handler).setRequiresReply(this.requiresReply);
			}
			else if (this.requiresReply && logger.isDebugEnabled()) {
			      logger.debug("requires-reply can only be set to AbstractReplyProducingMessageHandler or its subclass, "
			                     + handler.getComponentName() + " doesn't support it.");
			 }
		}
		if (!(handler instanceof AbstractMessageSplitter)) {
			Assert.isNull(this.applySequence, "Cannot set applySequence if the referenced bean is "
					+ "an AbstractReplyProducingMessageHandler, but not an AbstractMessageSplitter");
			Assert.isNull(this.delimiters, "Cannot set delimiters if the referenced bean is not an "
					+ "an AbstractReplyProducingMessageHandler, but not an AbstractMessageSplitter");
		}
		else {
			AbstractMessageSplitter splitter = (AbstractMessageSplitter) handler;
			if (this.delimiters != null) {
				Assert.isTrue(splitter instanceof DefaultMessageSplitter, "The 'delimiters' property is only available" +
						" for a Splitter definition where no 'ref', 'expression', or inner bean has been provided.");
				((DefaultMessageSplitter) splitter).setDelimiters(this.delimiters);
			}
			if (this.applySequence != null) {
				splitter.setApplySequence(applySequence);
			}
		}
	}

	@Override
	protected Class<? extends MessageHandler> getPreCreationHandlerType() {
		return AbstractMessageSplitter.class;
	}

}
