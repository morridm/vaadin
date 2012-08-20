/*
 * Copyright 2011 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.shared.communication;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Information needed by the framework to send an RPC method invocation from the
 * client to the server or vice versa.
 * 
 * @since 7.0
 */
public class MethodInvocation implements Serializable {

    private final String connectorId;
    private final String interfaceName;
    private final String methodName;
    private Object[] parameters;

    public MethodInvocation(String connectorId, String interfaceName,
            String methodName) {
        this.connectorId = connectorId;
        this.interfaceName = interfaceName;
        this.methodName = methodName;
    }

    public MethodInvocation(String connectorId, String interfaceName,
            String methodName, Object[] parameters) {
        this(connectorId, interfaceName, methodName);
        setParameters(parameters);
    }

    public String getConnectorId() {
        return connectorId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return connectorId + ":" + interfaceName + "." + methodName + "("
                + Arrays.toString(parameters) + ")";
    }

}