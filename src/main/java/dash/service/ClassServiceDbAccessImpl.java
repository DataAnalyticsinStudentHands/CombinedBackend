package dash.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.transaction.annotation.Transactional;

import dash.dao.ClassDao;
import dash.dao.ClassEntity;
import dash.errorhandling.AppException;
import dash.filters.AppConstants;
import dash.helpers.NullAwareBeanUtilsBean;
import dash.pojo.Location;
import dash.pojo.Class;
import dash.pojo.User;
import dash.security.CustomPermission;
import dash.security.GenericAclController;


public class ClassServiceDbAccessImpl extends ApplicationObjectSupport implements
ClassService {

	@Autowired
   ClassDao classDao;

	@Autowired
	private MutableAclService mutableAclService;

	@Autowired
	private GenericAclController<Class> aclController;
	
	@Autowired
	private GenericAclController<Location> groupAclController;

	/********************* Create related methods implementation ***********************/
	@Override
	@Transactional
	public Long createClass(Class clas) throws AppException {

		validateInputForCreation(clas);

		//verify existence of resource in the db (feed must be unique)
		ClassEntity classByName = classDao.getClassByName(clas.getName());
		if (classByName != null) {
			throw new AppException(
					Response.Status.CONFLICT.getStatusCode(),
					409,
					"Task with taskname already existing in the database with the id "
							+ classByName.getId(),
							"Please verify that the taskname and password are properly generated",
							AppConstants.DASH_POST_URL);
		}

		long classId = classDao.createClass(new ClassEntity(clas));
		clas.setId(classId);
		aclController.createACL(clas);
		aclController.createAce(clas, CustomPermission.MANAGER);
		return classId;
	}

	private void validateInputForCreation(Class clas) throws AppException {
		if (clas.getName() == null) {
			throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), 400, "Provided data not sufficient for insertion",
					"Please verify that the classname is properly generated/set",
					AppConstants.DASH_POST_URL);
		}
	}

	// ******************** Read related methods implementation **********************
	@Override
	public List<Class> getClasses(String orderByInsertionDate,
			Integer numberDaysToLookBack) throws AppException {

		//verify optional parameter numberDaysToLookBack first
		if(numberDaysToLookBack!=null){
			List<ClassEntity> recentClasses = classDao
					.getRecentClasses(numberDaysToLookBack);
			return getClassesFromEntities(recentClasses);
		}

		if(isOrderByInsertionDateParameterValid(orderByInsertionDate)){
			throw new AppException(
					Response.Status.BAD_REQUEST.getStatusCode(),
					400,
					"Please set either ASC or DESC for the orderByInsertionDate parameter",
					null, AppConstants.DASH_POST_URL);
		}
		List<ClassEntity> classes = classDao.getClasses(orderByInsertionDate);

		return getClassesFromEntities(classes);
	}
	
	@Override
	public List<Class> getClassesByLocation(Location location){
		
		List<ClassEntity> classes = classDao.getClassesByLocation(location);
		return getClassesFromEntities(classes);
		
	}	
	
	private boolean isOrderByInsertionDateParameterValid(
			String orderByInsertionDate) {
		return orderByInsertionDate!=null
				&& !("ASC".equalsIgnoreCase(orderByInsertionDate) || "DESC".equalsIgnoreCase(orderByInsertionDate));
	}
	
	@Override
	public Class verifyClassExistenceById(Long id) {
		ClassEntity classById = classDao.getClassById(id);
		if (classById == null) {
			return null;
		} else {
			return new Task(taskById);
		}
	}

	@Override
	public Task getTaskById(Long id) throws AppException {
		TaskEntity taskById = taskDao.getTaskById(id);
		if (taskById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The task you requested with id " + id
					+ " was not found in the database",
					"Verify the existence of the task with the id " + id
					+ " in the database", AppConstants.DASH_POST_URL);
		}

		return new Task(taskDao.getTaskById(id));
	}

	private List<Class> getClassesFromEntities(List<ClassEntity> classEntities) {
		List<Class> response = new ArrayList<Class>();
		for (ClassEntity classEntity : classEntities) {
			response.add(new Class(classEntity));
		}

		return response;
	}

	public List<Task> getRecentTasks(int numberOfDaysToLookBack) {
		List<TaskEntity> recentTasks = taskDao
				.getRecentTasks(numberOfDaysToLookBack);

		return getTasksFromEntities(recentTasks);
	}

	@Override
	public int getNumberOfTasks() {
		int totalNumber = taskDao.getNumberOfTasks();

		return totalNumber;

	}



	/********************* UPDATE-related methods implementation ***********************/
	@Override
	@Transactional
	public void updateFullyTask(Task task, Group group) throws AppException {
		//do a validation to verify FULL update with PUT
		

		Task verifyTaskExistenceById = verifyTaskExistenceById(task
				.getId());
		if (verifyTaskExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ task.getId(),
							AppConstants.DASH_POST_URL);
		}
		copyAllProperties(verifyTaskExistenceById, task);
		taskDao.updateTask(new TaskEntity(verifyTaskExistenceById));

	}

	private void copyAllProperties(Task verifyTaskExistenceById, Task task) {

		BeanUtilsBean withNull=new BeanUtilsBean();
		try {
			withNull.copyProperty(verifyTaskExistenceById, "description", task.getDescription());
			withNull.copyProperty(verifyTaskExistenceById, "name", task.getName());
			withNull.copyProperty(verifyTaskExistenceById, "time", task.getTime());
			withNull.copyProperty(verifyTaskExistenceById, "duration", task.getDuration());
			withNull.copyProperty(verifyTaskExistenceById, "location",  task.getLocation());
			withNull.copyProperty(verifyTaskExistenceById, "badge_id",  task.getBadge_id());
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/********************* DELETE-related methods implementation ***********************/

	@Override
	@Transactional
	public void deleteTask(Task task, Group group)  throws AppException{

		taskDao.deleteTaskById(task);
		aclController.deleteACL(task);

	}

	@Override
	@Transactional
	// TODO: This shouldn't exist? If it must, then it needs to accept a list of
	// Tasks to delete
	public void deleteTasks() {
		taskDao.deleteTasks();
	}
	
	/****************** Update Related Methods ***********************/

	

	@Override
	@Transactional
	public void updatePartiallyTask(Task task, Group group) throws AppException {
		//do a validation to verify existence of the resource
		Task verifyTaskExistenceById = verifyTaskExistenceById(task.getId());
		if (verifyTaskExistenceById == null) {
			throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
					404,
					"The resource you are trying to update does not exist in the database",
					"Please verify existence of data in the database for the id - "
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
		copyPartialProperties(verifyTaskExistenceById, task);
		taskDao.updateTask(new TaskEntity(verifyTaskExistenceById));

	}

	private void copyPartialProperties(Task verifyTaskExistenceById, Task task) {

		BeanUtilsBean notNull=new NullAwareBeanUtilsBean();
		try {
			notNull.copyProperties(verifyTaskExistenceById, task);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * ACL related methods
	 */
	// Adds an additional manager to the task
	@Override
	@Transactional
	public void addManager(User user, Task task, Group group) throws AppException{
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.createAce(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as manager because user is already manager of the group"
					+ "or they are not a member of the group to which this task belongs.",
					"Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
	}
	
	//Removes all managers and sets new manager to user
	@Override
	@Transactional
	public void resetManager(User user, Task task) throws AppException{
		Group group= new Group();
		group.setId(task.getGroup_id());
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.clearPermission(task, CustomPermission.MANAGER);
			aclController.createAce(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as manager because user is already manager of the group"
					+ "or they are not a member of the group to which this task belongs.",
					"Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
	}
	
	//Removes a single manager from a task
	@Override
	@Transactional
	public void deleteManager(User user, Task task, Group group) throws AppException{
		aclController.deleteACE(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
		aclController.createAce(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	//Adds a member to the task
	@Override
	@Transactional
	public void addMember(User user, Task task) throws AppException{
		Group group= new Group();
		group.setId(task.getGroup_id());
		if(isGroupManager(user, group) || isGroupMember(user, group)){
			aclController.createAce(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
			if(aclController.hasPermission(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername())))	
				aclController.deleteACE(task, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
		}else{
			throw new AppException(Response.Status.CONFLICT.getStatusCode(),
					409,
					"Cannot add user as member because user is already manager of the group"
					+ " or they are not a member of the group to which this task belongs.",
					" Users with group manager status may not have task specific permissions for that groups tasks"
							+ task.getId(), AppConstants.DASH_POST_URL);
		}
		
	}
	
	//Removes single member
	@Override
	@Transactional
	public void deleteMember(User user, Task task, Group group) throws AppException{
		aclController.deleteACE(task, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	
	
	/***********************  Helper Methods  **************************************/

	//Verifies that an user is not already a manager of the group
	//This avoids having a group manager also have task level permissions since they are redundant
	private boolean isGroupManager(User user, Group group){
		return groupAclController.hasPermission(group, CustomPermission.MANAGER, new PrincipalSid(user.getUsername()));
	}
	private boolean isGroupMember(User user, Group group){
		return groupAclController.hasPermission(group, CustomPermission.MEMBER, new PrincipalSid(user.getUsername()));
	}
	
	

}
