/*
 * Copyright 1999-2011 Alibaba Group.
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
package com.alibaba.dubbo.rpc.filter;

import java.util.UUID;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.json.JSON;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.logger.support.FailsafeLogger;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;

/**
 * 记录Service的Access Log。
 * <p>
 * 使用的Logger key是<code><b>dubbo.accesslog</b></code>。 如果想要配置Access
 * Log只出现在指定的Appender中，可以在Log4j中注意配置上additivity。配置示例: <code>
 * <pre>
 * &lt;logger name="<b>dubbo.accesslog</b>" <font color="red">additivity="false"</font>&gt;
 *    &lt;level value="info" /&gt;
 *    &lt;appender-ref ref="foo" /&gt;
 * &lt;/logger&gt;
 * </pre></code>
 * 
 * @author ding.lid
 * @author Dimmacro 使用此filter作为调用链的记录器，因此其应该作为第一个调用的filter
 */
@Activate(group = { Constants.PROVIDER, Constants.CONSUMER }, order = Integer.MIN_VALUE)
public class AccessLogFilter implements Filter {
	private static final String ACCLOG_PREFIX = "true";
	private static final String ACCLOG_SUFFIX = "-detail";
	
	private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);
	
	private static final String ACCESS_LOG_KEY = "dubbo.accesslog";

	public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
		String accesslog = invoker.getUrl().getParameter(Constants.ACCESS_LOG_KEY);
		if (ConfigUtils.isEmpty(accesslog) || !accesslog.startsWith(ACCLOG_PREFIX)) {
			return invoker.invoke(inv); // 如果没有配置accesslog或不是以true开头,则直接下一个filter调用
		}
		long start = System.currentTimeMillis();

		RpcContext context = RpcContext.getContext();
		String sideKey = invoker.getUrl().getParameter(Constants.SIDE_KEY);
		String traceID = this.buildTraceID(sideKey,context,inv);
		String pCallID = this.buildPCallID(traceID, sideKey,context,inv);
		String callID = this.buildCallID(pCallID, sideKey,context,inv);
		String clientHost = Constants.PROVIDER_SIDE.equalsIgnoreCase(sideKey)?context.getRemoteHost():"";
		int clientPort = Constants.PROVIDER_SIDE.equalsIgnoreCase(sideKey)?context.getRemotePort():0; //
		// 上面代码需要放在invoke之前调用
		
		Result result = invoker.invoke(inv);
		try {
			String serviceName = invoker.getInterface().getName();
			String version = invoker.getUrl().getParameter(Constants.VERSION_KEY);
			String group = invoker.getUrl().getParameter(Constants.GROUP_KEY);
			String applicationName = invoker.getUrl().getParameter(Constants.APPLICATION_KEY,invoker.getInterface().getName());

			StringBuilder sn = new StringBuilder(500); // 拼装调用链的json串
			sn.append("{").append("\"").append(Constants.APPLICATION_KEY).append("\"").append(":").append("\"").append(applicationName).append("\"").append(",");
			sn.append("\"").append(Constants.SERVER_KEY).append("\"").append(":").append("\"").append(invoker.getUrl().getHost()).append(Constants.KAFKA_SEPARATOR).append(invoker.getUrl().getPort()).append("\"").append(",");
			sn.append("\"").append(Constants.CLIENT_KEY).append("\"").append(":").append("\"").append(Constants.CONSUMER_SIDE.equalsIgnoreCase(sideKey)?context.getLocalHost():clientHost).append(Constants.KAFKA_SEPARATOR).append(Constants.CONSUMER_SIDE.equalsIgnoreCase(sideKey)?context.getLocalPort():clientPort).append("\"").append(",");
			sn.append("\"").append(Constants.SIDE_KEY).append("\"").append(":").append("\"").append(sideKey).append("\"").append(",");
			sn.append("\"").append(Constants.TRACE_ID).append("\"").append(":").append("\"").append(traceID).append("\"").append(",");
			sn.append("\"").append(Constants.PARENT_CALL_ID).append("\"").append(":").append("\"").append(pCallID).append("\"").append(",");
			sn.append("\"").append(Constants.CALL_ID).append("\"").append(":").append("\"").append(callID).append("\"").append(",");
			sn.append("\"").append(Constants.CALL_INFO).append("\"").append(":").append("\"");
			if (null != group && group.length() > 0) {
				sn.append(group).append(Constants.KAFKA_SEPARATOR);
			}
			sn.append(serviceName);
			if (null != version && version.length() > 0) {
				sn.append(Constants.KAFKA_SEPARATOR).append(version);
			}
			sn.append(Constants.KAFKA_SEPARATOR);
			sn.append(inv.getMethodName()).append("\"").append(","); // 上面增加的是调用信息group/interfacename/version/ method
			sn.append("\"").append("status").append("\"").append(":").append("\"").append(result.hasException() ?"Exception" : "OK").append("\"").append(",");
			if(result.hasException()){
				sn.append("\"").append("exceptionInfo").append("\"").append(":").append("\"").append(getMessageByFirstLine(result.getException().getMessage())).append("\"").append(",");
			}
			long totalElapse = System.currentTimeMillis() - start;
			// 判断是否设置了accesslogdetail信息
			if (accesslog.endsWith(ACCLOG_SUFFIX)) {
				sn.append("\"").append("detailInfo").append("\"").append(":{");
				sn.append("\"").append("methodParameterTypes").append("\"").append(":").append("\"");
				Class<?>[] types = inv.getParameterTypes();
				if (types != null && types.length > 0) {
					boolean first = true;
					for (Class<?> type : types) {
						if (first) {
							first = false;
						} else {
							sn.append(",");
						}
						sn.append(type.getName());
					}
				}
				sn.append("\"").append(",");
				sn.append("\"").append("methodArguments").append("\"").append(":");
				Object[] args = inv.getArguments();
				if (args != null && args.length > 0) {
					sn.append(JSON.json(args));
				}
				sn.append(","); // end methodParam
				sn.append("\"").append("detailResult").append("\"").append(":");
				long accesslogFilterElapse = System.currentTimeMillis()- result.getElapsed() - start;
				if(result instanceof RpcResult){
					((RpcResult)result).getFilterElapseList().add(this.getClass().getSimpleName()+":"+ accesslogFilterElapse+"ms");
				}
				totalElapse = result.getElapsed()+accesslogFilterElapse;
				result.setElapsed(totalElapse);
				sn.append(JSON.json(result));
				sn.append("}").append(",");
			} // end detail
			sn.append("\"").append("totalElapse").append("\"").append(":").append("\"").append(totalElapse).append("ms").append("\"");
			sn.append("}"); // end whole sn

			((FailsafeLogger)LoggerFactory.getLogger(ACCESS_LOG_KEY + "." + "AccesslogFilter")).getLogger().info(sn.toString());
			
		} catch (Throwable t) {
			logger.error("Exception in AcessLogFilter of service(" + invoker + " -> " + inv + ")", t);
		}
		return result;
	}

	private String getMessageByFirstLine(String message) {
		if(StringUtils.isEmpty(message)){
			return "";
		}
		String result ;
		int index = message.indexOf(System.lineSeparator());
		if(index <0){
			result = message;
		}else{
			result = message.substring(0,index);
		}
		return  result.replaceAll("\"", "\\\\\"");
	}

	// 常见callID
	private String buildCallID(String pCallID, String sideKey, RpcContext context,Invocation inv) {
		if (StringUtils.isEmpty(pCallID)) {
			return "";
		}
		String callID = inv.getAttachment(Constants.CALL_ID);
		if (Constants.CONSUMER_SIDE.equalsIgnoreCase(sideKey)) {
			if (StringUtils.isEmpty(callID)) {
				callID = Integer.parseInt(pCallID) +1+"" ;
				((RpcInvocation) inv).setAttachment(Constants.CALL_ID, callID);
			}
		} else {
			// 如果是服务端，需要将本次的callID设置到上下文里，这样此应用再往外调用的时候就可以使用
			context.setAttachment(Constants.CALL_ID, callID);
		}
		return callID;
	}

	// 创建parentCallID
	private String buildPCallID(String traceID, String sideKey, RpcContext context,Invocation inv) {
		if (StringUtils.isEmpty(traceID)) {
			return ""; // 如果没有traceID,直接返回pCallID为空，没有记录的必要性
		}
		String pCallID = inv.getAttachment(Constants.PARENT_CALL_ID); // 从上下文中获取pCallID,此值可能是作为server端的应用设置过来的
		if (Constants.CONSUMER_SIDE.equalsIgnoreCase(sideKey)) {
			if (StringUtils.isEmpty(pCallID)) {
				pCallID = context.getAttachment(Constants.CALL_ID); // 如果消费端没有pcallID，从上下文中看看是否此用用的server端放进去了
				if (StringUtils.isEmpty(pCallID)) {
					pCallID = "0"; // 如果pCallID为空，说明之前没有设置pCall，即此消费端为调用源头发起端
				}
				((RpcInvocation) inv).setAttachment(Constants.PARENT_CALL_ID, pCallID);
			}
		}
		return pCallID;
	}

	// 创建全局唯一的traceID
	private String buildTraceID(String sideKey, RpcContext context,Invocation inv) {
		String traceID = getFirstNotEmpty(inv.getAttachment(Constants.TRACE_ID),context.getAttachment(Constants.TRACE_ID));;
		if (Constants.CONSUMER_SIDE.equalsIgnoreCase(sideKey)) {
			// 如果是consumer端，要么从server端设置过来traceID，要么自己生成
			if (StringUtils.isEmpty(traceID)) {
				traceID = UUID.randomUUID().toString().replace("-", "");
				((RpcInvocation) inv).setAttachment(Constants.TRACE_ID, traceID); // 消费端设置traceID
			}
		}
		context.setAttachment(Constants.TRACE_ID, traceID); // 设置到上下文中，当由provider变为consumer时，此参数会带到下一环节
		// 如果是服务端没有traceID，则不处理
		return traceID;
	}

	// 获取第一个不是emtpy的对象
	private String getFirstNotEmpty(String value, String... values) {
		if(StringUtils.isNotEmpty(value)){
			return value;
		}
		for(String singleValue : values){
			if(StringUtils.isNotEmpty(singleValue)){
				return singleValue;
			}
		}
		return "";
	}

}