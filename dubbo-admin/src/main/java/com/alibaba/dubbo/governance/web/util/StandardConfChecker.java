package com.alibaba.dubbo.governance.web.util;


import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.support.ServletContextResource;

import com.alibaba.dubbo.common.Constants;

public class StandardConfChecker {
	protected static final Logger logger = Logger.getLogger(StandardConfChecker.class);

	private static final String propertiesFile = "/WEB-INF/standardConf.properties";
	
	private static Properties standardConfProperties;
	
	public static Properties getStandardConfProperties() {
		if (standardConfProperties == null) {
			synchronized (StandardConfChecker.class) {
				if (standardConfProperties == null) {
					try {
						Resource properResource = new ServletContextResource(ContextLoader.getCurrentWebApplicationContext().getServletContext(),propertiesFile);
						standardConfProperties = PropertiesLoaderUtils.loadProperties(new EncodedResource(properResource, Constants.DEFAULT_CHARSET));
					} catch (IOException e) {
						logger.error("",e);
					}
				}
			}
		}
		return standardConfProperties;
	}
	
	
	public static String getSpecialParameters(String parameters){
		String[] paramArray = parameters.split("&");
		StringBuffer sbBuffer = new StringBuffer();
		String[] paramPair;
		String paramValue;
		for(String param : paramArray){
			paramPair = param.split("=");
			if(2 == paramPair.length){
				paramValue = getStandardConfProperties().getProperty(paramPair[0]);
				if(null != paramValue && !paramValue.equalsIgnoreCase(paramPair[1])){
					sbBuffer.append(";").append(param).append("=>").append(paramValue).append(" ");
				}
			}
		}
		return sbBuffer.length()>0?sbBuffer.substring(1):"";
	}
}
