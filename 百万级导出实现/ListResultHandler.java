package cn.joysim.util.excel.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.poi.excel.ExcelWriter;
import cn.joysim.util.ExcelKit;
import cn.joysim.util.excel.converter.DataConverter;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: ganx
 * @date: 2019/11/4 16:52
 * @desc: 批量数据流式处理
 */
@Data
public class ListResultHandler<W extends ExcelWriter, T> implements ResultHandler<T> {

    private final Logger logger = LoggerFactory.getLogger(ListResultHandler.class);

    public static final Integer MAX_CACHE_NUM = 500;
    protected List<List<String>> dataCacheList = new ArrayList<>();
    protected int size = 0;

    private ExcelWriter excelWriter;

    private String sheetName;

    private DataConverter dataConverter;

    private int resultCount = 0;



    public ListResultHandler(W w, String sheetName, DataConverter dataConverter) {
        this.excelWriter = w;
        this.sheetName = sheetName;
        this.dataConverter = dataConverter;
    }


    @Override
    public void handleResult(ResultContext<? extends T> resultContext) {
        logger.info("resultCount:{}", resultContext.getResultCount());
        resultCount++;
        List<String> rowDataList = dataConverter.setData(resultContext.getResultObject());
        this.dataCacheList.add(rowDataList);
        size++;
        if (size == MAX_CACHE_NUM) {
            handle(resultContext.getResultCount() - MAX_CACHE_NUM);
        }
    }

    /**
     * 这个方法给外面调用，用来完成最后一批数据处理 以及excel文件写出
     */
    public File end(ExcelWriter writer, String fileName){
        //处理最后一批没有够执行的数据
        if (CollectionUtils.isNotEmpty(this.dataCacheList)) {
            handle(resultCount - this.dataCacheList.size());
        }
        //生成excel文件
        File file = null;
        Workbook workbook = writer.getWorkbook();
        if (!FileUtil.exist(fileName)) {
            file = FileUtil.newFile("D:/test/" + fileName + ".xlsx");
        } else {
            file = new File("D:/test/" + fileName + ".xlsx");
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)){
            workbook.write(fileOutputStream);
        } catch (Exception e) {
            logger.error("导出错误excel",e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                logger.error("关闭workbook",e);
            }
        }
        return file;
    }

    private void handle(int row) {
        try {
            // 获取到的批量结果数据进行需要的业务处理
            if (CollectionUtils.isNotEmpty(this.dataCacheList)) {
                logger.info("size:{},dataCacheList:{}", this.dataCacheList.size(), JSONObject.toJSONString(this.dataCacheList));
                ExcelKit.exposeBigExec(this.excelWriter, this.sheetName, this.dataCacheList, row);
            }
        } finally {
            // 处理完每批数据后后将临时清空
            size = 0;
            this.dataCacheList.clear();
        }
    }
}
