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
package io.jpom.controller.build;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import io.jpom.build.BuildExecuteService;
import io.jpom.build.BuildExtraModule;
import io.jpom.build.BuildUtil;
import io.jpom.build.ReleaseManage;
import io.jpom.common.BaseServerController;
import io.jpom.common.JsonMessage;
import io.jpom.common.validator.ValidatorConfig;
import io.jpom.common.validator.ValidatorItem;
import io.jpom.common.validator.ValidatorRule;
import io.jpom.model.BaseEnum;
import io.jpom.model.data.BuildInfoModel;
import io.jpom.model.enums.BuildStatus;
import io.jpom.model.log.BuildHistoryLog;
import io.jpom.model.user.UserModel;
import io.jpom.permission.ClassFeature;
import io.jpom.permission.Feature;
import io.jpom.permission.MethodFeature;
import io.jpom.service.dblog.BuildInfoService;
import io.jpom.service.dblog.DbBuildHistoryLogService;
import io.jpom.util.FileUtils;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Objects;

/**
 * new build info manage controller
 * ` *
 *
 * @author Hotstrip
 * @since 2021-08-23
 */
@RestController
@Feature(cls = ClassFeature.BUILD)
public class BuildInfoManageController extends BaseServerController {


    private final BuildInfoService buildInfoService;
    private final DbBuildHistoryLogService dbBuildHistoryLogService;
    private final BuildExecuteService buildExecuteService;

    public BuildInfoManageController(BuildInfoService buildInfoService,
                                     DbBuildHistoryLogService dbBuildHistoryLogService,
                                     BuildExecuteService buildExecuteService) {
        this.buildInfoService = buildInfoService;
        this.dbBuildHistoryLogService = dbBuildHistoryLogService;
        this.buildExecuteService = buildExecuteService;
    }

    /**
     * 开始构建
     *
     * @param id id
     * @return json
     */
    @RequestMapping(value = "/build/manage/start", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EXECUTE)
    public String start(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "没有数据") String id,
                        String buildRemark,
                        String resultDirFile,
                        String branchName,
                        String branchTagName,
                        String checkRepositoryDiff,
                        String projectSecondaryDirectory) {
        BuildInfoModel item = buildInfoService.getByKey(id, getRequest());
        Assert.notNull(item, "没有对应数据");
        // 更新数据
        BuildInfoModel update = new BuildInfoModel();
        Opt.ofBlankAble(resultDirFile).ifPresent(update::setResultDirFile);
        Opt.ofBlankAble(branchName).ifPresent(update::setBranchName);
        Opt.ofBlankAble(branchTagName).ifPresent(update::setBranchTagName);
        Opt.ofBlankAble(projectSecondaryDirectory).ifPresent(s -> {
            FileUtils.checkSlip(s, e -> new IllegalArgumentException("二级目录不能越级：" + e.getMessage()));
            //
            String extraData = item.getExtraData();
            JSONObject jsonObject = JSONObject.parseObject(extraData);
            jsonObject.put("projectSecondaryDirectory", s);
            update.setExtraData(jsonObject.toString());
        });

        if (!StrUtil.isAllBlank(resultDirFile, branchName, branchTagName, projectSecondaryDirectory)) {
            update.setId(id);
            buildInfoService.update(update);
        }
        // userModel
        UserModel userModel = getUser();
        // 执行构建
        return buildExecuteService.start(item.getId(), userModel, null, 0, buildRemark, checkRepositoryDiff).toString();
    }

    /**
     * 取消构建
     *
     * @param id id
     * @return json
     */
    @RequestMapping(value = "/build/manage/cancel", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EXECUTE)
    public JsonMessage<String> cancel(@ValidatorConfig(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "没有数据")) String id) {
        BuildInfoModel item = buildInfoService.getByKey(id, getRequest());
        Objects.requireNonNull(item, "没有对应数据");
        BuildStatus nowStatus = BaseEnum.getEnum(BuildStatus.class, item.getStatus());
        Objects.requireNonNull(nowStatus);
        if (BuildStatus.Ing != nowStatus && BuildStatus.PubIng != nowStatus) {
            return new JsonMessage<>(501, "当前状态不在进行中");
        }
        boolean status = buildExecuteService.cancelTask(item.getId());
        if (!status) {
            // 缓存中可能不存在数据,还是需要执行取消
            buildInfoService.updateStatus(id, BuildStatus.Cancel);
        }
        return JsonMessage.success("取消成功");
    }

    /**
     * 重新发布
     *
     * @param logId logId
     * @return json
     */
    @RequestMapping(value = "/build/manage/reRelease", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.EXECUTE)
    public JsonMessage<Object> reRelease(@ValidatorConfig(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "没有数据")) String logId) {
        BuildHistoryLog buildHistoryLog = dbBuildHistoryLogService.getByKey(logId, getRequest());
        Objects.requireNonNull(buildHistoryLog, "没有对应构建记录.");
        BuildInfoModel item = buildInfoService.getByKey(buildHistoryLog.getBuildDataId());
        Objects.requireNonNull(item, "没有对应数据");
        String e = buildExecuteService.checkStatus(item.getStatus());
        Assert.isNull(e, () -> e);
        UserModel userModel = getUser();
        BuildExtraModule buildExtraModule = BuildExtraModule.build(buildHistoryLog);
        //new BuildExtraModule();
        //buildExtraModule.updateValue(buildHistoryLog);
        ReleaseManage manage = ReleaseManage.builder()
            .buildExtraModule(buildExtraModule)
            .logId(buildHistoryLog.getId())
            .userModel(userModel)
            .buildNumberId(buildHistoryLog.getBuildNumberId())
            .buildExecuteService(buildExecuteService)
            .build();
        //ReleaseManage releaseManage = new ReleaseManage(buildHistoryLog, userModel);
        // 标记发布中
        //releaseManage.updateStatus(BuildStatus.PubIng);
        ThreadUtil.execute(manage);
        return JsonMessage.success("重新发布中");
    }

    /**
     * 获取构建的日志
     *
     * @param id      id
     * @param buildId 构建编号
     * @param line    需要获取的行号
     * @return json
     */
    @RequestMapping(value = "/build/manage/get-now-log", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @Feature(method = MethodFeature.LIST)
    public JsonMessage<JSONObject> getNowLog(@ValidatorItem(value = ValidatorRule.NOT_BLANK, msg = "没有数据") String id,
                                             @ValidatorItem(value = ValidatorRule.POSITIVE_INTEGER, msg = "没有buildId") int buildId,
                                             @ValidatorItem(value = ValidatorRule.POSITIVE_INTEGER, msg = "line") int line) {
        BuildInfoModel item = buildInfoService.getByKey(id, getRequest());
        Assert.notNull(item, "没有对应数据");
        Assert.state(buildId <= item.getBuildId(), "还没有对应的构建记录");

        BuildHistoryLog buildHistoryLog = new BuildHistoryLog();
        buildHistoryLog.setBuildDataId(id);
        buildHistoryLog.setBuildNumberId(buildId);
        BuildHistoryLog queryByBean = dbBuildHistoryLogService.queryByBean(buildHistoryLog);
        Assert.notNull(queryByBean, "没有对应的构建历史");

        File file = BuildUtil.getLogFile(item.getId(), buildId);
        Assert.state(FileUtil.isFile(file), "日志文件错误");

        if (!file.exists()) {
            if (buildId == item.getBuildId()) {
                return new JsonMessage<>(201, "还没有日志文件");
            }
            return new JsonMessage<>(300, "日志文件不存在");
        }
        JSONObject data = FileUtils.readLogFile(file, line);
        // 运行中
        Integer status = queryByBean.getStatus();
        data.put("run", status == BuildStatus.Ing.getCode() || status == BuildStatus.PubIng.getCode());
        // 构建中
        data.put("buildRun", status == BuildStatus.Ing.getCode());

        return JsonMessage.success("ok", data);
    }
}