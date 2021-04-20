package cn.joysim.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import cn.joysim.enums.BatchStateEnum;
import cn.joysim.order.client.SuningClient;
import cn.joysim.order.exception.OrderException;
import cn.joysim.order.exception.status.OrderStatusCode;
import cn.joysim.order.mapper.TbOrderMapper;
import cn.joysim.order.model.dto.*;
import cn.joysim.order.model.pojo.TbBatch;
import cn.joysim.order.model.pojo.TbOrder;
import cn.joysim.order.model.pojo.TbOrderCode;
import cn.joysim.order.model.pojo.TbOrderPush;
import cn.joysim.order.service.*;
import cn.joysim.order.thread.OrderSchedule;
import cn.joysim.order.thread.OrderThreadFactory;
import cn.joysim.util.*;
import cn.joysim.util.excel.handler.ListResultHandler;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import org.apache.http.client.utils.DateUtils;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 *  服务实现类
 * @author joysim
 * @since 2019-10-17
 */
@Service
public class TbOrderServiceImpl extends ServiceImpl<TbOrderMapper, TbOrder> implements ITbOrderService {

    private final Logger logger = LoggerFactory.getLogger(TbOrderServiceImpl.class);

    /**
     * 订单兑换码最大请求数量
     */
    private final static int PUSH_MAX_QUANTITY = 100;
    /**
     * 接口重试次数
     */
    private final static int API_RETRY_TIMES = 2;

    @Resource
    SqlSessionTemplate sqlSessionTemplate;

    @Resource
    private ITbBatchService batchService;

    @Resource
    private ITbOrderSynHistoryService orderSynHistoryService;

    @Resource
    private ITbOrderCodeService orderCodeService;

    @Resource
    private ITbOrderPushService orderPushService;

    @Override
    public TbBatch getCodes(TbBatch batch, OrderPushDto dto, HttpServletResponse response) {
        try {
            Long time = System.currentTimeMillis();
            if (ObjectUtils.isNull(dto, dto.getAmount(), dto.getEffectiveDays(), dto.getQuantity())) {
                logger.error("参数不完整");
                throw new OrderException(OrderStatusCode.PARAM_UNCOMPLETE);
            }
            //开启线程推送订单
            int orderNum = (dto.getQuantity() % PUSH_MAX_QUANTITY == 0)
                    ? dto.getQuantity() / PUSH_MAX_QUANTITY : dto.getQuantity() / PUSH_MAX_QUANTITY + 1;

            //开启线程请求获取苏宁兑换码接口
            //初始化orderNum个list装载
            List<TbOrder> orderList = new ArrayList<>();
            for (int i = 0; i < orderNum; i++) {
                //生成批次订单
                TbOrder order = new TbOrder();
                order.init();
                order.setId(IdWorker.getId());
                order.setBatchId(batch.getId());
                order.setAmount(dto.getAmount());
                order.setMobile(dto.getPhone());
                order.setEffectiveDays(dto.getEffectiveDays());
                order.setOrderTime(dto.getRequestTime());
                order.setQuantity(PUSH_MAX_QUANTITY);
                if (i == orderNum - 1) {
                    order.setQuantity(dto.getQuantity() % PUSH_MAX_QUANTITY == 0 ? PUSH_MAX_QUANTITY : dto.getQuantity() % PUSH_MAX_QUANTITY);
                }
                orderList.add(order);
            }
            //加入订单任务
            List<CallableTask<TbOrderCode>> taskList = new ArrayList<>();
            if (CollUtil.isNotEmpty(orderList)) {
                logger.info("待执行批次[{}]订单大小：{}，orderList：{}", batch.getId(), orderList.size(), orderList);
                //开启多线程执行，一个线程10个订单
                List<List<TbOrder>> parts = Lists.partition(orderList, 10);
                parts.forEach(k -> taskList.add(OrderThreadFactory.getBatchOrderCodes(k)));
            } else {
                logger.info("没有执行的订单数据");
            }
            logger.info("主线程在执行任务");
            List<Future<List<TbOrderCode>>> result = new ArrayList<>();
            List<TbOrderCode> codeList = new ArrayList<>();
            result = OrderSchedule.me().execute(taskList);
            logger.info("Future 结果：{}", JSONObject.toJSONString(result));
            if (CollUtil.isNotEmpty(result)) {
                result.forEach((Future<List<TbOrderCode>> res) -> {
                    try {
                        logger.info("task运行结果" + res.get());
                        codeList.addAll(res.get());
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("task运行子线程结果解析异常：{}", e.getMessage(), e);
                    } catch (RejectedExecutionException e) {
                        logger.error("task运行子线程任务数量最大值：{}", e.getMessage(), e);
                    }
                });
            }
            logger.info("所有任务执行完毕，耗时：{}s", System.currentTimeMillis() - time);
            //重试该批次的失败订单，如果存在
            checkAndRetryFailedOrder(codeList, batch.getId());
            logger.info("我可以输出加密压缩包了");
            try {
                createCodeZip(codeList, batch.getId(), response);
            } catch (IOException e) {
                logger.error("浏览器中止了一个输出压缩包的连接");
                throw new OrderException(OrderStatusCode.CLIENT_END_DOWNLOAD);
            } catch (Exception e) {
                logger.error("输出加密压缩包异常：{}", e.getMessage(), e);
                throw new OrderException(OrderStatusCode.SERCER_ERROR);
            }
        } catch (OrderException e) {
            throw e;
        } finally {
            //无论失败成功，都释放改批次状态
            logger.info("执行完毕，更新批次状态：执行完毕");
            TbBatch updateBatch = new TbBatch();
            updateBatch.setId(batch.getId());
            updateBatch.setUpdateTime(new Date());
            updateBatch.setState(BatchStateEnum.ZXWB.getCode());
            batchService.updateById(updateBatch);
            return batch;
        }
    }

    /**
     * 重试批次失败订单，限定重试次数
     * @param codeList
     * @param batchId
     */
    private void checkAndRetryFailedOrder(List<TbOrderCode> codeList, Long batchId) {
        for (int i = 0; i < API_RETRY_TIMES; i++) {
            //失败单较少，不开线程重试
            List<TbOrder> orderList = baseMapper.listFailedOrdersByBatch(batchId);
            logger.info("重试该批次[{}]的失败订单大小：{}，orderList：{}", batchId, orderList.size(), JSONObject.toJSONString(orderList));
            if (CollUtil.isEmpty(orderList)) {
                break;
            }
            codeList.addAll(orderApiPush(orderList, true));
        }
    }

    /**
     * 输出加密压缩兑换码文件
     * @param codeList
     */
    private void createCodeZip(List<TbOrderCode> codeList, Long batchId, HttpServletResponse response) throws Exception {
        StringBuilder sb = new StringBuilder();
        codeList.forEach((TbOrderCode code) -> {
            sb.append(code.getCodeId())
                    .append(",")
                    .append(code.getCode())
                    .append("\r\n");
        });
        //创建批次文件夹
        String fileName = "codes.txt";
        String codeFileDir = SntbConfig.getDownloadAbsPath().replace("\\", File.separator).replace("/", File.separator)
                + File.separator + batchId;
        String zipFileName = SntbConfig.getDownloadAbsPath().replace("\\", File.separator).replace("/", File.separator)
                + File.separator + batchId +".zip";
        String codeFileRealPath = codeFileDir + File.separator + fileName;
        if (!FileUtil.existDirectory(codeFileDir)) {
            //文件或者文件夹不存在，创建
            FileUtil.mkDir(codeFileDir);
        }
        logger.info("开始写入codes.txt，生成压缩加密zip");
        FileUtil.write(codeFileRealPath, sb.toString());
        ZipSlf4jUtil.compress(zipFileName, codeFileDir, "E9B5CFA6008A36DD95A9315E24BD1BC5");
        //下载文件
        logger.info("开始输出下载文件");
        FileUtil.download(response, zipFileName, DateUtils.formatDate(new Date(), DatePattern.PURE_DATETIME_PATTERN)+"_"+batchId, ".zip");
        logger.info("我已经输出文件了");
        //删除本地codes.txt
        try {
            FileUtil.delDir(codeFileDir);
        } catch (Exception e) {
            logger.error("删除本地删除本地codes.txt所在文件夹失败:{}", e.getMessage(), e);
        }
        logger.info("我删除本地删除本地codes.txt文件了");
    }

    @Override
    public void downloadCodesByBatch(Long batchId, HttpServletResponse response) {
        TbBatch batch = batchService.getById(batchId);
        if (ObjectUtils.isEmpty(batch)) {
            throw new OrderException(OrderStatusCode.BATCH_NO_DATA);
        }
        String zipFileName = SntbConfig.getDownloadAbsPath().replace("\\", File.separator).replace("/", File.separator)
                + File.separator + batchId +".zip";
        try {
            FileUtil.download(response, zipFileName, DateUtils.formatDate(new Date(), DatePattern.PURE_DATETIME_PATTERN)+"_"+batchId, ".zip");
        } catch (IOException e) {
            logger.error("浏览器中止了一个输出压缩包的连接");
            throw new OrderException(OrderStatusCode.CLIENT_END_DOWNLOAD);
        } catch (Exception e) {
            logger.error("输出加密压缩包异常：{}", e.getMessage(), e);
            throw new OrderException(OrderStatusCode.SERCER_ERROR);
        }
        logger.info("执行完毕，更新批次状态：执行成功");
        TbBatch updateBatch = new TbBatch();
        updateBatch.setId(batchId);
        updateBatch.setUpdateTime(new Date());
        updateBatch.setState(BatchStateEnum.ZXWB.getCode());
        batchService.updateById(updateBatch);
    }

    @Override
    public TbBatch checkAndCreateBatch(OrderPushDto dto) {
        TbBatch batch = new TbBatch();
        try {
            //校验是否存在执行中的批次
            TbBatch ingBatch = batchService.getIngBatch();
            if (ObjectUtils.isNotEmpty(ingBatch)) {
                logger.info("操作过于频繁，有批次的子线程正在执行中");
                throw new OrderException(OrderStatusCode.ORDER_ING_BATCH_ERROR);
            }
            //校验余额状态
            AccountInfo account = getAccountBalance();
            Double payItem = DoubleUtil.mul(Double.valueOf(dto.getAmount()), dto.getQuantity().doubleValue());
            if (payItem > account.getAvailableAmount()) {
                throw new OrderException(OrderStatusCode.BALANCE_ERROR);
            }
            logger.info("当前余额：{}，可以操作下载", account.getAvailableAmount());
            //生成批次
            BeanUtils.copyProperties(dto, batch);
            batch.init();
            batch.setId(IdWorker.getId());
            batch.setRequestTime(dto.getRequestTime());
            batch.setState(BatchStateEnum.ZXZ.getCode());
            batchService.save(batch);
        } catch (OrderException e) {
            throw e;
        } catch (Exception e) {
            logger.error("代码锁出现异常：{}", e.getMessage(), e);
            e.printStackTrace();
        }
        return batch;
    }

    @Override
    public TbBatch retryGetCodes(Long batchId) {
        return null;
    }

    @Override
    public void retryByBatch(Long batchId, HttpServletResponse response) {
        List<TbOrderCode> codeList = new ArrayList<>();
        TbBatch batch = batchService.getById(batchId);
        if (ObjectUtils.isEmpty(batch)) {
            throw new OrderException(OrderStatusCode.BATCH_NO_DATA);
        }
        checkAndRetryFailedOrder(codeList, batchId);
        if (CollUtil.isNotEmpty(codeList)) {
            logger.info("我可以删除本地加密压缩包了");
            String zipFileName = SntbConfig.getDownloadAbsPath().replace("\\", File.separator).replace("/", File.separator)
                    + File.separator + batchId +".zip";
            FileUtil.delFile(zipFileName);
            logger.info("我可以输出加密压缩包了");
            try {
                createCodeZip(codeList, batchId, response);
            } catch (IOException e) {
                logger.error("浏览器中止了一个输出压缩包的连接");
                throw new OrderException(OrderStatusCode.CLIENT_END_DOWNLOAD);
            } catch (Exception e) {
                logger.error("输出加密压缩包异常：{}", e.getMessage(), e);
                throw new OrderException(OrderStatusCode.SERCER_ERROR);
            }
        } else {
            logger.info("没有code可以导出下载");
        }
    }

    @Override
    public AccountInfo getAccountBalance() {
        QueryAccountResponseDto dto = new QueryAccountResponseDto();
        AccountInfo accountInfo = new AccountInfo();
        try {
            dto = SuningClient.queryAccount();
        } catch (Exception e) {
            logger.error("调取苏宁API查询账户余额异常：[{}]", e.getMessage(), e);
        }
        //解析返回报文，并数据入库
        //dealGetAccountBalanceResult(dto));
        //接口调用信息
        TbOrderPush push = new TbOrderPush();
        push.init();
        BeanUtils.copyProperties(dto, push);
        orderPushService.save(push);
        if (CollUtil.isNotEmpty(dto.getAccountInfo())) {
            dto.getAccountInfo().forEach((AccountInfo info) ->{
                if ("01".equals(info.getAccountType())) {
                    BeanUtils.copyProperties(info, accountInfo);
                    //单位换算，分转元
                    accountInfo.setAvailableAmount(
                            DoubleUtil.div(accountInfo.getAvailableAmount().doubleValue(), 100L, 2)
                    );
                }
            });
        }
        return accountInfo;
    }

    @Override
    public List<TbOrderCode> listBatchCode(Long batchId) {
        Date startDate = new Date();
        String sheetName = "20191206";
        String fileName = "测试";
        //写入器
        ExcelWriter writer = ExcelUtil.getBigWriter(ListResultHandler.MAX_CACHE_NUM);
        //数据转换器
        OrderCodeConverter converter = new OrderCodeConverter();
        //初始化工作表和表头
        ExcelKit.initSheet(writer, sheetName, converter.getHeader());
        //查询
        ListResultHandler<ExcelWriter, TbOrderCode> handler = new ListResultHandler<>(writer, sheetName, converter);
        sqlSessionTemplate.select("cn.joysim.order.mapper.TbOrderMapper.listBatchCode", null , handler);
        //输出excel文件
        handler.end(writer, fileName);
        Date endDate = new Date();
        logger.info("------查询时间：{}--------", endDate.getTime() - startDate.getTime());
        return null;
    }

    @Override
    public List<TbOrderCode> orderApiPush(List<TbOrder> orderList, Boolean isRetry) {

        List<TbOrderCode> codeList = new ArrayList<>();
        orderList.forEach((TbOrder order) -> {
            GetCodesResponseDto responseDto = new GetCodesResponseDto();
            try {
                responseDto = SuningClient.sendSuningTicket(String.valueOf(order.getId()),
                        order.getAmount(), order.getQuantity(), order.getEffectiveDays(), order.getMobile());
            } catch (Exception e) {
                logger.error("调取苏宁API获取苏宁铜板兑换码异常：[{}]", e.getMessage(), e);
            }
            //解析返回报文，并数据入库
            codeList.addAll(dealGetCodeResult(order, responseDto, isRetry));
        });
        return codeList;
    }

    /**
     * 解析返回报文，并数据入库：获取或代发铜板兑换码接口
     * @param order
     * @param baseOrderResonse
     * @param isRetry 重试操作
     */
    private List<TbOrderCode> dealGetCodeResult(TbOrder order, GetCodesResponseDto baseOrderResonse, boolean isRetry) {
        List<TbOrderCode> codeList = new ArrayList<>();
        Long batchId = order.getBatchId();
        Long orderId = order.getId();
        if (ObjectUtils.isNull(baseOrderResonse)) {
            logger.info("获取或代发铜板兑换码接口数据为空");
            orderSynHistoryService.saveOrderSynHistory(batchId, orderId, "-1", "验证结果签名不一致或者其他问题");
            if (isRetry) {
                baseMapper.updateById(order);
            } else {
                baseMapper.insert(order);
            }
            return codeList;
        }
        //接口调用信息
        TbOrderPush push = new TbOrderPush();
        push.init();
        push.setBatchId(batchId);
        push.setOrderId(orderId);
        BeanUtils.copyProperties(baseOrderResonse, push);
        orderPushService.save(push);

        if (ObjectUtils.isNotEmpty(baseOrderResonse.getResponseCode())) {
            orderSynHistoryService.saveOrderSynHistory(batchId, orderId, baseOrderResonse.getResponseCode(), baseOrderResonse.getMessage());
            if (SuningClient.SUCCESS_CODE.equals(baseOrderResonse.getResponseCode())) {
                order.setSuningOrderNo(baseOrderResonse.getOrderNo());
                //解密codeInfo
                List<OrderCodeInfoDto> codeDtoList = baseOrderResonse.getCodeInfo();
                if (CollUtil.isNotEmpty(codeDtoList)) {
                    codeDtoList.forEach((OrderCodeInfoDto codeInfoDto) -> {
                        TbOrderCode code = new TbOrderCode();
                        BeanUtils.copyProperties(codeInfoDto, code);
                        code.init();
                        code.setBatchId(batchId);
                        code.setOrderId(orderId);
                        code.setId(IdWorker.getId());
                        //解密code，覆盖
                        try {
                            code.setCode(SuningClient.decryptCode(codeInfoDto.getCode()));
                        } catch (Exception e) {
                            //失败，存的是未解密的code，避免丢失数据
                            code.setCode(codeInfoDto.getCode());
                            logger.error("私钥解密兑换码code异常", e.getMessage(), e);
                        }
                        codeList.add(code);
                    });
                } else {
                    logger.info("返回加密code为空");
                }
            }
        } else {
            orderSynHistoryService.saveOrderSynHistory(batchId, orderId, baseOrderResonse.getErrorCode(), baseOrderResonse.getErrorMsg());
        }
        //数据入库
        if (isRetry) {
            baseMapper.updateById(order);
        } else {
            baseMapper.insert(order);
        }
        if (CollUtil.isNotEmpty(codeList)) {
            List<List<TbOrderCode>> parts = Lists.partition(codeList, 50);
            parts.forEach(k ->{
                orderCodeService.saveBatch(k);
            });
        } else {
            logger.info("没有入库的兑换码数据");
        }
        return codeList;
    }


}
