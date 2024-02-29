/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.hutool.core.io.unit.DataSizeUtil;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.InvocationBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.dromara.jpom.DockerUtil;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * https://docs.docker.com/engine/api/v1.41/#operation/ContainerKill
 * <p>
 * https://docs.docker.com/engine/api/v1.41/#operation/ContainerUpdate
 *
 * @author bwcx_jzy
 * @since 2022/1/25
 */
public class TestLocal {
    private DockerClient dockerClient;
    private AuthConfig authConfig;

    @Before
    public void init() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.INFO);

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withRegistryUsername(null)
            // .withDockerHost("tcp://192.168.163.11:2376").build();
//				.withApiVersion()
//            .withDockerHost("tcp://127.0.0.1:2375")
            .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .maxConnections(100)
//				.connectionTimeout(Duration.ofSeconds(30))
//				.responseTimeout(Duration.ofSeconds(45))
            .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        //
        authConfig = new AuthConfig();
        authConfig.withRegistryAddress("registry.cn-shanghai.aliyuncs.com");
        authConfig.withEmail("bwcx_jzy@163.com111");
        authConfig.withPassword("xxx");
        authConfig.withUsername("bwcx_jzy@163.com");
    }

    @Test
    public void test() {

        dockerClient.pingCmd().exec();
        VersionCmd versionCmd = dockerClient.versionCmd();
        Version exec = versionCmd.exec();
        System.out.println(exec);
    }

    @Test
    public void tset2() throws InterruptedException {
        StatsCmd statsCmd = dockerClient.statsCmd("socat");
        Statistics statistics = statsCmd.exec(new InvocationBuilder.AsyncResultCallback<>()).awaitResult();
        System.out.println(statistics);
        System.out.println(JSONObject.toJSONString(statistics));
    }

    @Test
    public void test3() {
//        dockerClient.inspectContainerCmd("socat")
        UpdateContainerCmd containerCmd = dockerClient.updateContainerCmd("socat");
//        containerCmd.withCpusetCpus("1");
//        containerCmd.withCpusetMems("1");
//        containerCmd.withCpuPeriod(1);
//        containerCmd.withCpuQuota(1);
//        containerCmd.withCpuShares(1);
//        containerCmd.withBlkioWeight(1);
//        containerCmd.withMemoryReservation(DataSizeUtil.parse("10M"));
//        containerCmd.withKernelMemory(DataSizeUtil.parse("10M"));
//        containerCmd.withMemory(DataSizeUtil.parse("10M"));
//        containerCmd.withMemorySwap(DataSizeUtil.parse("10M"));


        UpdateContainerResponse containerResponse = containerCmd.exec();
        System.out.println(containerResponse);
    }

    @Test
    public void testSize() {
        System.out.println(DataSizeUtil.parse("-1"));
        System.out.println(DataSizeUtil.parse("0"));
    }

    @Test
    public void test4() {
        InspectContainerCmd socat = dockerClient.inspectContainerCmd("socat").withSize(true);
        InspectContainerResponse exec = socat.exec();
        System.out.println(JSONObject.toJSONString(exec.getHostConfig(), JSONWriter.Feature.PrettyFormat));
    }

    @Test
    public void testAuth() {
        AuthConfig authConfig = dockerClient.authConfig();
        System.out.println(authConfig);

        //
        // Info exec = dockerClient.infoCmd().exec();
        //System.out.println(JSONObject.toJSONString(exec));
        //

        AuthResponse authResponse = dockerClient.authCmd().withAuthConfig(authConfig).exec();
        System.out.println(authResponse);
    }

    @Test
    public void testPull() throws InterruptedException {
        PullImageCmd imageCmd = dockerClient.pullImageCmd("registry.cn-shanghai.aliyuncs.com/jpom-demo/jpomtestdocker:1.0");
        AuthResponse authResponse = dockerClient.authCmd()
            //.withAuthConfig(authConfig)
            .exec();
        imageCmd.withAuthConfig(authConfig);
        imageCmd.exec(new InvocationBuilder.AsyncResultCallback<PullResponseItem>() {
            @Override
            public void onNext(PullResponseItem object) {
                String responseItem = DockerUtil.parseResponseItem(object);
                System.out.println(responseItem);
            }

        }).awaitCompletion();

    }

    @Test
    public void testConfig() {

    }
}
