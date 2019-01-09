package com.wisezone.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.wisezone.entity.Group;
import com.wisezone.entity.Leave;
import com.wisezone.entity.MemberShip;
import com.wisezone.entity.MyTask;
import com.wisezone.entity.PageBean;
import com.wisezone.entity.User;
import com.wisezone.service.LeaveService;
import com.wisezone.util.ResponseUtil;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;

/**
 * 任务Controller层
 * @author user
 *
 */
@Controller
@RequestMapping("/task")
public class TaskController {

	@Resource
	private TaskService taskService;
	
	@Resource
	private RepositoryService repositoryService;
	
	@Resource
	private RuntimeService runtimeService;
	
	@Resource
	private FormService formService;
	
	@Resource
	private LeaveService leaveService;
	
	@Resource
	private HistoryService historyService;

	/**
	 * 根据用户id分页查询任务
	 * @param page
	 * @param rows
	 * @param userId 用户id
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/list")
	public String list(@RequestParam(value="page",required=false)String page,
				@RequestParam(value="rows",required=false)String rows,
				String s_taskName,
				String userId,
				HttpServletResponse response)throws Exception{
		System.out.println(userId+"-----------------------");
		if(s_taskName==null){
			s_taskName="";
		}
		PageBean pageBean=new PageBean(Integer.parseInt(page),Integer.parseInt(rows));
		List<Task> list=taskService.createTaskQuery()  // 创建任务查询
				.taskCandidateUser(userId) // 根据用户id查询
				.taskNameLike("%"+s_taskName+"%") // 根据任务名称模糊查询
				.listPage(pageBean.getStart(),pageBean.getPageSize()); // 返回带分页的结果集合 
		//分页查询任务对象
		List<MyTask> taskList=new ArrayList<MyTask>();
		for(Task t:list){
			MyTask myTask=new MyTask();
			myTask.setId(t.getId());
			myTask.setName(t.getName());
			myTask.setCreateTime(t.getCreateTime());
			taskList.add(myTask);
        }
		long total=taskService.createTaskQuery().taskCandidateUser(userId).taskNameLike("%"+s_taskName+"%").count(); // 获取总记录数
		JsonConfig jsonConfig=new JsonConfig();
		jsonConfig.registerJsonValueProcessor(java.util.Date.class, new DateJsonValueProcessor("yyyy-MM-dd hh:mm:ss"));
		JSONObject result=new JSONObject();
		JSONArray jsonArray=JSONArray.fromObject(taskList,jsonConfig);
		result.put("rows", jsonArray);
		result.put("total", total);
		ResponseUtil.write(response, result);
		return null;
	}
	
	/**
	 * 流程实例已经走完的历史任务
	 * @param page
	 * @param rows
	 * @param s_taskName
	 * @param groupId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/finishedList")
	public String finishedList(@RequestParam(value="page",required=false)String page,@RequestParam(value="rows",required=false)String rows,String s_taskName,String groupId,HttpServletResponse response)throws Exception{
		if(s_taskName==null){
			s_taskName="";
		}
		PageBean pageBean=new PageBean(Integer.parseInt(page),Integer.parseInt(rows));
		List<HistoricTaskInstance> htiList=historyService.createHistoricTaskInstanceQuery() // 创建历史任务实例查询
				.taskCandidateGroup(groupId) // 根据角色查询
				.taskNameLike("%"+s_taskName+"%") // 根据任务名称模糊查询
				.listPage(pageBean.getStart(),pageBean.getPageSize()); // 返回带分页的结果集合
		List<MyTask> taskList=new ArrayList<MyTask>();
		int t=0; // 不满足条件的个数
		for(HistoricTaskInstance hti:htiList){
			// 如果该任务对应的流程实例在运行时任务表里查询不到，说明就是这个流程实例已经走完
		    if(taskService.createTaskQuery().processInstanceId(hti.getProcessInstanceId()).singleResult()==null){  
		    	MyTask myTask=new MyTask();
		    	myTask.setId(hti.getId());
		    	myTask.setName(hti.getName());
		    	myTask.setCreateTime(hti.getCreateTime());
		    	myTask.setEndTime(hti.getEndTime());
		    	taskList.add(myTask);
		    }else{
		    	t++;
		    }
        }
		long total=historyService.createHistoricTaskInstanceQuery().taskCandidateGroup(groupId).taskNameLike("%"+s_taskName+"%").count(); // 获取总记录数
		JsonConfig jsonConfig=new JsonConfig();
		jsonConfig.registerJsonValueProcessor(java.util.Date.class, new DateJsonValueProcessor("yyyy-MM-dd hh:mm:ss"));
		JSONObject result=new JSONObject();
		JSONArray jsonArray=JSONArray.fromObject(taskList,jsonConfig);
		result.put("rows", jsonArray);
		result.put("total", total-t);
		ResponseUtil.write(response, result);
		return null;
	}
	
	
	/**
	 * 流程实例未走完的历史任务 已办任务
	 * @param page
	 * @param rows
	 * @param s_taskName
	 * @param groupId
	 * @param userId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/unFinishedList")
	public String unFinishedList(@RequestParam(value="page",required=false)String page,@RequestParam(value="rows",required=false)String rows,String s_taskName,String groupId,String userId,HttpServletResponse response)throws Exception{
		System.out.println("已办任务");
		if(s_taskName==null){
			s_taskName="";
		}
		PageBean pageBean=new PageBean(Integer.parseInt(page),Integer.parseInt(rows));
		List<HistoricTaskInstance> htiList=historyService.createHistoricTaskInstanceQuery() // 创建历史任务实例查询
				.taskCandidateGroup(groupId) // 根据角色查询
				.taskNameLike("%"+s_taskName+"%") // 根据任务名称模糊查询
				.listPage(pageBean.getStart(),pageBean.getPageSize()); // 返回带分页的结果集合
		List<MyTask> taskList=new ArrayList<MyTask>();
		int t=0; // 不满足条件的个数
		for(HistoricTaskInstance hti:htiList){
			// 如果该任务对应的流程实例在运行时任务表里查询到，说明就是这个流程实例未走完  并且用用户id以及任务id在运行时候任务表里查询不到结果  才算是已办任务
		    if((taskService.createTaskQuery().processInstanceId(hti.getProcessInstanceId()).singleResult()!=null)
		    		&&(taskService.createTaskQuery().taskCandidateUser(userId).taskId(hti.getId()).list().size()==0)){  
		    	MyTask myTask=new MyTask();
		    	myTask.setId(hti.getId());
		    	myTask.setName(hti.getName());
		    	myTask.setCreateTime(hti.getCreateTime());
		    	myTask.setEndTime(hti.getEndTime());
		    	taskList.add(myTask);
		    }else{
		    	t++;
		    }
        }
		long total=historyService.createHistoricTaskInstanceQuery().taskCandidateGroup(groupId).taskNameLike("%"+s_taskName+"%").count(); // 获取总记录数
		JsonConfig jsonConfig=new JsonConfig();
		jsonConfig.registerJsonValueProcessor(java.util.Date.class, new DateJsonValueProcessor("yyyy-MM-dd hh:mm:ss"));
		JSONObject result=new JSONObject();
		JSONArray jsonArray=JSONArray.fromObject(taskList,jsonConfig);
		result.put("rows", jsonArray);
		result.put("total", total-t);
		ResponseUtil.write(response, result);
		return null;
	}
	
	/**
	 * 查询当前流程图 
	 * @param taskId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/showCurrentView")
	public ModelAndView showCurrentView(String taskId,HttpServletResponse response)throws Exception{
		ModelAndView mav = new ModelAndView(); 
		// 通过任务id查询流程定义
		Task task=taskService.createTaskQuery() // 创建任务查询
				.taskId(taskId) // 根据任务id查询
				.singleResult();
		String processDefinitionId=task.getProcessDefinitionId(); // 获取流程定义id
		ProcessDefinition processDefinition=repositoryService.createProcessDefinitionQuery() // 创建流程定义查询
					.processDefinitionId(processDefinitionId) // 根据流程定义id查询
					.singleResult();
		mav.addObject("deploymentId",processDefinition.getDeploymentId()); // 部署id
		mav.addObject("diagramResourceName", processDefinition.getDiagramResourceName()); // 图片资源文件名称
		
		// 查看当前活动坐标
		ProcessDefinitionEntity processDefinitionEntity =(ProcessDefinitionEntity) repositoryService.getProcessDefinition(processDefinitionId);
		String processInstanceId = task.getProcessInstanceId(); // 获取流程实例id
		// 根据流程实例id查询活动实例
		ProcessInstance pi =runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId) 
				.singleResult();
		ActivityImpl activityImpl = processDefinitionEntity.findActivity(pi.getActivityId()); // 根据活动id查询活动实例
		mav.addObject("x", activityImpl.getX());
		mav.addObject("y", activityImpl.getY());
		mav.addObject("width", activityImpl.getWidth());
		mav.addObject("height", activityImpl.getHeight());
		mav.setViewName("page/currentView");
		return mav;
	}
	
	/**
	 * 查询当前流程图 待办用到
	 * @param taskId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/showHisCurrentView")
	public ModelAndView showHisCurrentView(String taskId,HttpServletResponse response)throws Exception{
		ModelAndView mav = new ModelAndView(); 
		HistoricTaskInstance hti=historyService
						.createHistoricTaskInstanceQuery()
							.taskId(taskId)
								.singleResult(); //获取历史任务实例对象
		String processDefinitionId=hti.getProcessDefinitionId(); // 获取流程定义id
		ProcessDefinition processDefinition=repositoryService
				.createProcessDefinitionQuery() // 创建流程定义查询
					.processDefinitionId(processDefinitionId) 
					// 根据流程定义id查询
					.singleResult();
		mav.addObject("deploymentId",processDefinition.getDeploymentId()); // 部署id
		mav.addObject("diagramResourceName", 
					processDefinition.getDiagramResourceName()); // 图片资源文件名称
		// 查看当前活动坐标
		ProcessDefinitionEntity processDefinitionEntity =
				(ProcessDefinitionEntity) 
				repositoryService.getProcessDefinition(processDefinitionId);
		String processInstanceId = hti.getProcessInstanceId(); // 获取流程实例id
		// 根据流程实例id查询活动实例
		ProcessInstance pi =runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId) 
				.singleResult();
		ActivityImpl activityImpl = 
				processDefinitionEntity
				.findActivity(pi.getActivityId()); // 根据活动id查询活动实例
		mav.addObject("x", activityImpl.getX());
		mav.addObject("y", activityImpl.getY());
		mav.addObject("width", activityImpl.getWidth());
		mav.addObject("height", activityImpl.getHeight());
		mav.setViewName("page/currentView");
		return mav;
	}
	
	
	/**
	 * 重定向审核处理页面
	 * @param taskId
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/redirectPage")
	public String redirectPage(String taskId,HttpServletResponse response)throws Exception{
		/*
		 * https://blog.csdn.net/whatlookingfor/article/details/52998861
		 * activiti提供了FormService的API操作可以获取到流程启动
		 * 节点的表单内容以及任务节点的表单内容
		 */
		TaskFormData formData = formService.getTaskFormData(taskId);
		//根据流程定义Id获取流程启动节点的表单内容
		String url = formData.getFormKey(); 
		//获取audit_Id.jsp
		System.out.println("路径地址为："+url);
		JSONObject result=new JSONObject();
		result.put("url", url);
		ResponseUtil.write(response, result);
		return null;
	}
	

	/**
	 * 班长审批
	 * @param taskId 任务id
	 * @param leaveDays 请假天数
	 * @param comment 批注信息 
	 * @param state 审核状态 1 通过 or 2 驳回
	 * @param response
	 * @param session
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/audit_bz")
	public String audit_bz(String taskId,Integer leaveDays,String comment,Integer state,HttpServletResponse response,HttpSession session)throws Exception{
		// 通过任务id查询流程定义
		System.out.println("班长方法"+taskId+leaveDays+comment+state);
		Task task=taskService.createTaskQuery() // 创建任务查询
				.taskId(taskId) // 根据任务id查询
				.singleResult();
		Map<String, Object> variables=new HashMap<String,Object>();
		if(state==1){
			variables.put("msg", "通过");			
		}else if(state==2){
			Integer leaveId=(Integer) taskService.getVariable(taskId, "leaveId"); // 通过任务id获取请假单id
			Leave leave=leaveService.findById(leaveId);
			leave.setState("审核未通过");
			leaveService.update(leave); // 更新审核信息
			variables.put("msg", "未通过");	
		}
		variables.put("days", leaveDays); // 流程变量值
		String processInstanceId = task.getProcessInstanceId(); // 获取流程实例id
	/*	MemberShip currentMemberShip=(MemberShip)session.getAttribute("currentMemberShip");
		User currentUser=currentMemberShip.getUser();
		Group currentGroup=currentMemberShip.getGroup();*/
		 Session sess = SecurityUtils.getSubject().getSession();
		 String firstname = (String) sess.getAttribute("firstName");
		 String lastname = (String) sess.getAttribute("lastName");
		 String group = (String)sess.getAttribute("group");
		 System.out.println("________"+firstname+lastname+group);
		Authentication.setAuthenticatedUserId(
				 firstname
				+lastname
				+"【"+group+"】");// 设置用户id
		taskService.addComment(taskId, processInstanceId, comment); // 添加批注信息
		taskService.complete(taskId, variables); // 完成任务
		JSONObject result=new JSONObject();
		result.put("success", true);
		ResponseUtil.write(response, result);
		return null;
	}
	
	/**
	 * 领导审批
	 * @param taskId 任务id
	 * @param leaveDays 请假天数
	 * @param comment 批注信息 
	 * @param state 审核状态 1 通过 or 2 驳回
	 * @param response
	 * @param session
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/audit_ld")
	public String audit_ld(String taskId,Integer leaveDays,String comment,Integer state,HttpServletResponse response,HttpSession session)throws Exception{
		// 通过任务id查询流程定义
		System.out.println("领导状态"+state);
		Task task=taskService.createTaskQuery() // 创建任务查询
				.taskId(taskId) // 根据任务id查询
				.singleResult();
		Map<String, Object> variables=new HashMap<String,Object>();
		Integer leaveId=(Integer) taskService.getVariable(taskId, "leaveId"); // 通过任务id获取请假单id
		Leave leave=leaveService.findById(leaveId);
		if(state==1){
			leave.setState("审核通过");
			leaveService.update(leave); // 更新审核信息		
		}else if(state==2){
			leave.setState("审核未通过");
			leaveService.update(leave); // 更新审核信息
		}
		String processInstanceId = task.getProcessInstanceId(); // 获取流程实例id
/*		MemberShip currentMemberShip=(MemberShip)session.getAttribute("currentMemberShip");
		User currentUser=currentMemberShip.getUser();
		Group currentGroup=currentMemberShip.getGroup();*/
		 Session sess = SecurityUtils.getSubject().getSession();
		 String firstname = (String) sess.getAttribute("firstName");
		 String lastname = (String) sess.getAttribute("lastName");
		 String group = (String)sess.getAttribute("group");
		Authentication.setAuthenticatedUserId(firstname+ lastname+"【"+group+"】");// 设置用户id
		taskService.addComment(taskId, processInstanceId, comment); // 添加批注信息
		taskService.complete(taskId, variables); // 完成任务
		JSONObject result=new JSONObject();
		result.put("success", true);
		ResponseUtil.write(response, result);
		return null;
	}

	
	/**
	 * 历史批注 
	 * @param taskId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/listHistoryComment")
	public String listHistoryComment(String taskId,HttpServletResponse response)throws Exception{
		System.out.println("4444");
		if(taskId==null){
			return null;
		}
		HistoricTaskInstance hti=historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
		String processInstanceId = hti.getProcessInstanceId(); // 获取流程实例id
		List<Comment> commentList=taskService.getProcessInstanceComments(processInstanceId);
		Collections.reverse(commentList); // 集合元素反转
		JsonConfig jsonConfig=new JsonConfig();
		jsonConfig.registerJsonValueProcessor(java.util.Date.class, new DateJsonValueProcessor("yyyy-MM-dd hh:mm:ss"));
		JSONObject result=new JSONObject();
		JSONArray jsonArray=JSONArray.fromObject(commentList,jsonConfig);
		System.out.println("查看历史批注");
		result.put("rows", jsonArray);
		ResponseUtil.write(response, result);
		return null;
	}
	
	
	/**
	 * 历史批注 通过流程实例id
	 * @param taskId
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/listHistoryCommentWithProcessInstanceId")
	public String listHistoryCommentWithProcessInstanceId(String processInstanceId,HttpServletResponse response)throws Exception{
		if(processInstanceId==null){
			return null;
		}
		System.out.println("新鲜出炉"+processInstanceId);
		List<Comment> commentList=taskService.getProcessInstanceComments(processInstanceId);
		Collections.reverse(commentList); // 集合元素反转
		JsonConfig jsonConfig=new JsonConfig();
		jsonConfig.registerJsonValueProcessor(java.util.Date.class, new DateJsonValueProcessor("yyyy-MM-dd hh:mm:ss"));
		JSONObject result=new JSONObject();
		JSONArray jsonArray=JSONArray.fromObject(commentList,jsonConfig);
		result.put("rows", jsonArray);
		ResponseUtil.write(response, result);
		return null;
	}
}
