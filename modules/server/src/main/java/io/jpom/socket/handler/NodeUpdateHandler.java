/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Code Technology Studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jpom.socket.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.FileResource;
import cn.hutool.core.lang.Tuple;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.jpom.common.Const;
import io.jpom.common.JpomManifest;
import io.jpom.common.JsonMessage;
import io.jpom.common.Type;
import io.jpom.common.forward.NodeForward;
import io.jpom.common.forward.NodeUrl;
import io.jpom.model.AgentFileModel;
import io.jpom.model.WebSocketMessageModel;
import io.jpom.model.data.NodeModel;
import io.jpom.permission.ClassFeature;
import io.jpom.permission.Feature;
import io.jpom.permission.MethodFeature;
import io.jpom.permission.SystemPermission;
import io.jpom.service.node.NodeService;
import io.jpom.service.system.SystemParametersServer;
import io.jpom.socket.BaseProxyHandler;
import io.jpom.socket.ConsoleCommandOp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;
import top.jpom.transport.DataContentType;
import top.jpom.transport.IProxyWebSocket;
import top.jpom.transport.IUrlItem;
import top.jpom.transport.TransportServerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 节点管理控制器
 *
 * @author lf
 */
@SystemPermission(superUser = true)
@Feature(cls = ClassFeature.UPGRADE_NODE_LIST, method = MethodFeature.EXECUTE)
@Slf4j
public class NodeUpdateHandler extends BaseProxyHandler {

    private final ConcurrentMap<String, IProxyWebSocket> clientMap = new ConcurrentHashMap<>();

    private static final int CHECK_COUNT = 120;

    /**
     * 初始等待时间
     */
    private static final int INIT_WAIT = 10 * 1000;

    private final SystemParametersServer systemParametersServer;
    private final NodeService nodeService;

    public NodeUpdateHandler(NodeService nodeService, SystemParametersServer systemParametersServer) {
        super(null);
        this.nodeService = nodeService;
        this.systemParametersServer = systemParametersServer;
        //systemParametersServer = SpringUtil.getBean(SystemParametersServer.class);
//        nodeService = SpringUtil.getBean(NodeService.class);
    }

    @Override
    protected void init(WebSocketSession session, Map<String, Object> attributes) throws Exception {
        super.init(session, attributes);

    }

    @Override
    protected Object[] getParameters(Map<String, Object> attributes) {
        return new Object[]{};
    }

    @Override
    protected void showHelloMsg(Map<String, Object> attributes, WebSocketSession session) {

    }

    private void pullNodeList(WebSocketSession session, String ids) {
        List<String> split = StrUtil.split(ids, StrUtil.COMMA);
        List<NodeModel> nodeModelList = nodeService.listById(split);
        if (nodeModelList == null) {
            this.onError(session, "没有查询到节点信息：" + ids);
            return;
        }
        for (NodeModel model : nodeModelList) {
            IProxyWebSocket nodeClient = clientMap.computeIfAbsent(model.getId(), s -> {
                IUrlItem urlItem = NodeForward.createUrlItem(model, NodeUrl.NodeUpdate, DataContentType.FORM_URLENCODED);

                IProxyWebSocket proxySession = TransportServerFactory.get().websocket(model, urlItem);
                proxySession.onMessage(msg -> sendMsg(session, msg));
                return proxySession;
            });
            // 连接节点
            ThreadUtil.execute(() -> {
                try {
                    if (!nodeClient.isConnected()) {
                        nodeClient.reconnectBlocking();
                    }
                    WebSocketMessageModel command = new WebSocketMessageModel("getVersion", model.getId());
                    nodeClient.send(command.toString());
                } catch (Exception e) {
                    log.error("创建插件端连接失败", e);
                    this.onError(session, "连接插件端失败：" + model.getName() + "  " + e.getMessage());
                }
            });
        }
    }

    @Override
    public void destroy(WebSocketSession session) {
        clientMap.values().forEach(iProxyWebSocket -> {
            if (iProxyWebSocket.isConnected()) {
                try {
                    iProxyWebSocket.close();
                } catch (Exception e) {
                    log.error("关闭连接异常", e);
                }
            }
        });
        clientMap.clear();
        //
        super.destroy(session);
    }

    @Override
    protected String handleTextMessage(Map<String, Object> attributes, WebSocketSession session, JSONObject json, ConsoleCommandOp consoleCommandOp) throws IOException {
        WebSocketMessageModel model = WebSocketMessageModel.getInstance(json.toString());
        String command = model.getCommand();
        switch (command) {
            case "getAgentVersion":
                model.setData(getAgentVersion());
                break;
            case "updateNode":
                super.logOpt(this.getClass(), attributes, json);
                updateNode(model, session);
                break;
            case "heart":
                for (Map.Entry<String, IProxyWebSocket> entry : clientMap.entrySet()) {
                    String key = entry.getKey();
                    IProxyWebSocket iProxyWebSocket = entry.getValue();
                    try {
                        iProxyWebSocket.send(model.toString());
                    } catch (Exception e) {
                        log.error("心跳消息转发失败 {} {}", key, e.getMessage());
                    }
                }
                break;
            default: {
                if (StrUtil.startWith(command, "getNodeList:")) {
                    String ids = StrUtil.removePrefix(command, "getNodeList:");
                    if (StrUtil.isNotEmpty(ids)) {
                        pullNodeList(session, ids);
                    }
                }
            }
            break;
        }
        if (model.getData() != null) {
            return model.toString();
        }
        return null;
    }

    private void onError(WebSocketSession session, String msg) {
        WebSocketMessageModel error = new WebSocketMessageModel("onError", "");
        error.setData(msg);
        this.sendMsg(error, session);
    }

    /**
     * 更新节点
     *
     * @param model 参数
     */
    private void updateNode(WebSocketMessageModel model, WebSocketSession session) {
        JSONObject params = (JSONObject) model.getParams();
        JSONArray ids = params.getJSONArray("ids");
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        String protocol = params.getString("protocol");
        boolean http = StrUtil.equalsIgnoreCase(protocol, "http");
        try {
            AgentFileModel agentFileModel = systemParametersServer.getConfig(AgentFileModel.ID, AgentFileModel.class);
            //
            if (agentFileModel == null || !FileUtil.exist(agentFileModel.getSavePath())) {
                this.onError(session, "Agent JAR包不存在");
                return;
            }
            JsonMessage<Tuple> error = JpomManifest.checkJpomJar(agentFileModel.getSavePath(), Type.Agent, false);
            if (error.getCode() != HttpStatus.HTTP_OK) {
                this.onError(session, "Agent JAR 损坏请重新上传," + error.getMsg());
                return;
            }
            for (int i = 0; i < ids.size(); i++) {
                int finalI = i;
                ThreadUtil.execute(() -> this.updateNodeItem(ids.getString(finalI), session, agentFileModel, http));
            }
        } catch (Exception e) {
            log.error("升级失败", e);
        }
    }

    private boolean updateNodeItemHttp(NodeModel nodeModel, WebSocketSession session, AgentFileModel agentFileModel) {
        File file = FileUtil.file(agentFileModel.getSavePath());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("file", new FileResource(file));
        JsonMessage<String> message = NodeForward.request(nodeModel, NodeUrl.SystemUploadJar, jsonObject);
        String id = nodeModel.getId();
        WebSocketMessageModel callbackRestartMessage = new WebSocketMessageModel("restart", id);
        callbackRestartMessage.setData(message.getMsg());
        this.sendMsg(callbackRestartMessage, session);
        // 先等待一会，太快可能还没重启
        ThreadUtil.sleep(INIT_WAIT);
        int retryCount = 0;
        try {
            while (retryCount <= CHECK_COUNT) {
                ++retryCount;
                try {
                    ThreadUtil.sleep(1000L);
                    JsonMessage<Object> jsonMessage = NodeForward.request(nodeModel, NodeUrl.Info, "nodeId", id);
                    if (jsonMessage.success()) {
                        this.sendMsg(callbackRestartMessage.setData("重启完成"), session);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("{} 节点连接失败 {}", id, e.getMessage());
                }
            }
            this.sendMsg(callbackRestartMessage.setData("重连失败"), session);
        } catch (Exception e) {
            log.error("升级后重连插件端失败:" + id, e);
            this.sendMsg(callbackRestartMessage.setData("重连插件端失败"), session);
        }
        return false;
    }

    private boolean updateNodeItemWebSocket(IProxyWebSocket client, String id, WebSocketSession session, AgentFileModel agentFileModel) throws IOException {
        // 发送文件信息
        WebSocketMessageModel webSocketMessageModel = new WebSocketMessageModel("upload", id);
        webSocketMessageModel.setNodeId(id);
        webSocketMessageModel.setParams(agentFileModel);
        client.send(webSocketMessageModel.toString());
        //
        try (FileInputStream fis = new FileInputStream(agentFileModel.getSavePath())) {
            // 发送文件内容
            int len;
            byte[] buffer = new byte[Const.DEFAULT_BUFFER_SIZE];
            while ((len = fis.read(buffer)) > 0) {
                client.send(ByteBuffer.wrap(buffer, 0, len));
            }
        }
        WebSocketMessageModel restartMessage = new WebSocketMessageModel("restart", id);
        client.send(restartMessage.toString());
        // 先等待一会，太快可能还没重启
        ThreadUtil.sleep(INIT_WAIT);
        // 重启后尝试访问插件端，能够连接说明重启完毕
        //WebSocketMessageModel callbackRestartMessage = new WebSocketMessageModel("restart", id);
        int retryCount = 0;
        try {
            while (retryCount <= CHECK_COUNT) {
                try {
                    if (client.reconnect()) {
                        this.sendMsg(restartMessage.setData("重启完成"), session);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("{} 节点连接失败 {}", id, e.getMessage());
                }
                ThreadUtil.sleep(1000L);
                ++retryCount;
            }
            this.sendMsg(restartMessage.setData("重连失败"), session);
        } catch (Exception e) {
            log.error("升级后重连插件端失败:" + id, e);
            this.sendMsg(restartMessage.setData("重连插件端失败"), session);
        }
        return false;
    }

    private void updateNodeItem(String id, WebSocketSession session, AgentFileModel agentFileModel, boolean http) {
        try {
            NodeModel node = nodeService.getByKey(id);
            if (node == null) {
                this.onError(session, "没有对应的节点：" + id);
                return;
            }
            IProxyWebSocket client = clientMap.get(node.getId());
            if (client == null) {
                this.onError(session, "对应的插件端还没有被初始化：" + id);
                return;
            }
            if (client.isConnected()) {
                boolean result = http ? this.updateNodeItemHttp(node, session, agentFileModel) : this.updateNodeItemWebSocket(client, id, session, agentFileModel);
                if (result) {
                    //
                    WebSocketMessageModel command = new WebSocketMessageModel("getVersion", node.getId());
                    client.send(command.toString());
                }
            } else {
                this.onError(session, node.getName() + " 节点连接丢失");
            }
        } catch (Exception e) {
            log.error("升级失败:" + id, e);
            this.onError(session, "节点升级失败：" + e.getMessage());
        }
    }

    private void sendMsg(WebSocketMessageModel model, WebSocketSession session) {
        super.sendMsg(session, model.toString());
    }

    /**
     * 获取当前系统缓存的Agent
     *
     * @return json
     */
    private String getAgentVersion() {
        AgentFileModel agentFileModel = systemParametersServer.getConfig(AgentFileModel.ID, AgentFileModel.class, agentFileModel1 -> {
            if (agentFileModel1 == null || !FileUtil.exist(agentFileModel1.getSavePath())) {
                return AgentFileModel.EMPTY;
            }
            return agentFileModel1;
        });
        return JSONObject.toJSONString(agentFileModel);
    }
}