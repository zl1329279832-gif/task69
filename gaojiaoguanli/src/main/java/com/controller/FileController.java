package com.controller;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.annotation.IgnoreAuth;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.entity.ConfigEntity;
import com.entity.EIException;
import com.service.ConfigService;
import com.utils.R;

/**
 * 上传文件映射表
 */
@RestController
@RequestMapping("file")
@SuppressWarnings({"unchecked","rawtypes"})
public class FileController{
	@Autowired
    private ConfigService configService;

	/**
	 * 文件上传外置目录（从 config.properties 或外置配置读取）
	 * 若配置路径不存在，回退到 ServletContext.getRealPath("/upload")
	 */
	@Value("${upload.base.path:}")
	private String uploadBasePath;

	/**
	 * 获取上传目录，优先使用外置路径，回退到 WAR 内目录
	 */
	private File getUploadDir(HttpServletRequest request) {
		if (StringUtils.isNotBlank(uploadBasePath)) {
			File dir = new File(uploadBasePath);
			if (dir.exists() || dir.mkdirs()) {
				return dir;
			}
		}
		// 回退：使用 WAR 内目录（开发环境兼容）
		return new File(request.getSession().getServletContext().getRealPath("/upload"));
	}

	/**
	 * 上传文件
	 */
	@RequestMapping("/upload")
	public R upload(@RequestParam("file") MultipartFile file, String type,HttpServletRequest request) throws Exception {
		if (file.isEmpty()) {
			throw new EIException("上传文件不能为空");
		}
		String fileExt = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")+1);
		String fileName = new Date().getTime()+"."+fileExt;
		File dest = new File(getUploadDir(request), fileName);
		file.transferTo(dest);
		if(StringUtils.isNotBlank(type) && type.equals("1")) {
			ConfigEntity configEntity = configService.selectOne(new EntityWrapper<ConfigEntity>().eq("name", "faceFile"));
			if(configEntity==null) {
				configEntity = new ConfigEntity();
				configEntity.setName("faceFile");
				configEntity.setValue(fileName);
			} else {
				configEntity.setValue(fileName);
			}
			configService.insertOrUpdate(configEntity);
		}
		return R.ok().put("file", fileName);
	}

	/**
	 * 下载文件
	 */
	@IgnoreAuth
	@RequestMapping("/download")
	public void download(@RequestParam String fileName, HttpServletRequest request, HttpServletResponse response) {
		try {
			File file = new File(getUploadDir(request), fileName);
			if (file.exists()) {
				response.reset();
				response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName+"\"");
				response.setHeader("Cache-Control", "no-cache");
				response.setHeader("Access-Control-Allow-Credentials", "true");
				response.setContentType("application/octet-stream; charset=UTF-8");
				IOUtils.write(FileUtils.readFileToByteArray(file), response.getOutputStream());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
