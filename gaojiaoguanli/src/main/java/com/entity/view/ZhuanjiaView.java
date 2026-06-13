package com.entity.view;

import com.entity.ZhuanjiaEntity;

import com.baomidou.mybatisplus.annotations.TableName;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 专家
 * 后端返回视图实体辅助类
 * （通常后端关联的表或者自定义的字段需要返回使用）
 */
@TableName("zhuanjia")
public class ZhuanjiaView extends ZhuanjiaEntity implements Serializable {
    private static final long serialVersionUID = 1L;
		/**
		* 性别的值
		*/
		private String sexValue;
		/**
		* 专家学科类型的值
		*/
		private String zhuanjiaValue;



	public ZhuanjiaView() {

	}

	public ZhuanjiaView(ZhuanjiaEntity zhuanjiaEntity) {
		try {
			BeanUtils.copyProperties(this, zhuanjiaEntity);
		} catch (IllegalAccessException | InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



			/**
			* 获取： 性别的值
			*/
			public String getSexValue() {
				return sexValue;
			}
			/**
			* 设置： 性别的值
			*/
			public void setSexValue(String sexValue) {
				this.sexValue = sexValue;
			}

			/**
			* 获取： 专家学科类型的值
			*/
			public String getZhuanjiaValue() {
				return zhuanjiaValue;
			}
			/**
			* 设置： 专家学科类型的值
			*/
			public void setZhuanjiaValue(String zhuanjiaValue) {
				this.zhuanjiaValue = zhuanjiaValue;
			}









}
