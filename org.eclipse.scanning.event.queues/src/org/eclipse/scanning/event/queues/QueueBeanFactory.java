package org.eclipse.scanning.event.queues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.scanning.api.event.queues.IQueueBeanFactory;
import org.eclipse.scanning.api.event.queues.beans.IHasAtomQueue;
import org.eclipse.scanning.api.event.queues.beans.QueueAtom;
import org.eclipse.scanning.api.event.queues.beans.SubTaskAtom;
import org.eclipse.scanning.api.event.queues.beans.TaskBean;
import org.eclipse.scanning.api.event.queues.models.QueueModelException;
import org.eclipse.scanning.api.event.queues.models.QueueableModel;
import org.eclipse.scanning.api.event.queues.models.SubTaskAtomModel;
import org.eclipse.scanning.api.event.queues.models.TaskBeanModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueBeanFactory implements IQueueBeanFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(QueueBeanFactory.class);
	
	private List<String> queueAtomShortNameRegistry;
	
	private Map<String, QueueAtom> queueAtomRegistry;
	private Map<String, SubTaskAtomModel> subTaskModelRegistry;
	private Map<String, TaskBeanModel> taskBeanModelRegistry;
	
	private String defaultTaskBeanShortName;
	private boolean explicitDefaultTaskBean = false;
	
	public QueueBeanFactory() {
		queueAtomShortNameRegistry = new ArrayList<>();
		
		queueAtomRegistry = new HashMap<>();
		subTaskModelRegistry = new HashMap<>();
		taskBeanModelRegistry = new HashMap<>();
	}

	@Override
	public <Q extends QueueAtom> void registerAtom(Q atom) throws QueueModelException{
		String atomShortName = atom.getShortName();
		if (queueAtomShortNameRegistry.contains(atomShortName)) {
			logger.error("Cannot register atom. An atom with the reference '"+atomShortName+"' is already registered.");
			throw new QueueModelException("An atom with the reference '"+atomShortName+"' is already registered.");
		}
		
		queueAtomRegistry.put(atomShortName, atom);
		queueAtomShortNameRegistry.add(atomShortName);
	}

	@Override
	public void unregisterAtom(String reference) throws QueueModelException {
		//Atom could either be a real QueueAtom or a SubTaskModel...
		if (!queueAtomShortNameRegistry.contains(reference)) {
			logger.error("Cannot unregister atom. No atom registered for reference '"+reference+"'.");
			throw new QueueModelException("No atom registered for reference '"+reference+"'.");
		} else {
			if (queueAtomRegistry.containsKey(reference)) {
				queueAtomRegistry.remove(reference);
				queueAtomShortNameRegistry.remove(reference);
			} else if (subTaskModelRegistry.containsKey(reference)) {
				subTaskModelRegistry.remove(reference);
				queueAtomShortNameRegistry.remove(reference);
			}
		}
	}

	@Override
	public void registerAtom(SubTaskAtomModel subTask) throws QueueModelException {
		String subTaskShortName = subTask.getShortName();
		if (queueAtomShortNameRegistry.contains(subTaskShortName)) {
			logger.error("Cannot register SubTaskAtomModel. An atom with the reference '"+subTaskShortName+"' is already registered.");
			throw new QueueModelException("An atom with the reference '"+subTaskShortName+"' is already registered.");
		}
		subTaskModelRegistry.put(subTaskShortName, subTask);
		queueAtomShortNameRegistry.add(subTaskShortName);
	}

	@Override
	public void registerTask(TaskBeanModel task) throws QueueModelException {
		String taskShortName = task.getShortName();
		if (taskBeanModelRegistry.containsKey(taskShortName)) {
			logger.error("Cannot register TaskBeanModel. A TaskBeanModel with reference '"+taskShortName+"' is already registered.");
			throw new QueueModelException("A TaskBeanModel with reference '"+taskShortName+"' is already registered.");
		}
		taskBeanModelRegistry.put(taskShortName, task);
		
		/*
		 * Decide whether we should set the default TaskbeanModel by 
		 * implication or not...
		 */
		if (taskBeanModelRegistry.size() == 1) {
			//Don't change the setting of explicit here, there's no need and this would be a side-effect
			defaultTaskBeanShortName = task.getShortName();
		} else if (!explicitDefaultTaskBean) {
				defaultTaskBeanShortName = null;
		}
		/*
		 * Otherwise an explicit default has been set and we should leave the 
		 * current default TaskBean model alone
		 */
	}

	@Override
	public void unregisterTask(String reference) throws QueueModelException {
		if (taskBeanModelRegistry.containsKey(reference)) {
			taskBeanModelRegistry.remove(reference);
			if (defaultTaskBeanShortName == reference) defaultTaskBeanShortName = null;
			return;
		}
		logger.error("Cannot unregister TaskBeanModel. No TaskBeanModel registered for reference '"+reference+"'");
		throw new QueueModelException("No TaskBeanModel registered for reference '"+reference+"'");
	}

	@Override
	public List<String> getQueueAtomRegister() {
		return queueAtomShortNameRegistry;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <Q extends QueueAtom> Q getQueueAtom(String reference) throws QueueModelException {
		if (queueAtomShortNameRegistry.contains(reference)) {
			if (queueAtomRegistry.containsKey(reference)) {
				return (Q)queueAtomRegistry.get(reference);
			}
			if (subTaskModelRegistry.containsKey(reference)) {
				return (Q)assembleSubTask(reference);
			}
		}
		logger.error("No QueueAtom with the short name "+reference+" found in QueueAtom registry.");
		throw new QueueModelException("No QueueAtom with the short name "+reference+" found in QueueAtom registry.");
	}

	@Override
	public SubTaskAtom assembleSubTask(String reference) throws QueueModelException {
		SubTaskAtomModel stModel = subTaskModelRegistry.get(reference);
		if (stModel == null) {
			logger.error("Failed to assemble SubTaskAtom: No SubTaskAtomModel registered for reference'"+reference+"'");
			throw new QueueModelException("No SubTaskAtomModel registered for reference'"+reference+"'");
		}
		
		SubTaskAtom stAtom = new SubTaskAtom(reference, stModel.getName());
		populateAtomQueue(stModel, stAtom);
		
		return stAtom;
	}

	@Override
	public TaskBean assembleTaskBean(String reference) throws QueueModelException {
		TaskBeanModel tbModel = taskBeanModelRegistry.get(reference);
		if (tbModel == null) {
			logger.error("Failed to assemble TaskBean: No TaskBeanModel registered for reference'"+reference+"'");
			throw new QueueModelException("No TaskBeanModel registered for reference'"+reference+"'");
		}
		
		TaskBean tBean = new TaskBean(reference, tbModel.getName());
		populateAtomQueue(tbModel, tBean);
		
		return tBean;
	}
	
	/**
	 * Used by assembleX methods to get atoms in the queueAtomShortNames Lists 
	 * of a given {@link TaskBeanModel} or {@link SubTaskAtomModel} and put 
	 * them into a new, real atomQueue in an instance of {@link TaskBean} or 
	 * {@link SubTaskAtom} (respectively). 
	 * @param model {@link QueueableModel} instance containing atom list
	 * @param queueHolder {@link IHasAtomQueue} instance to be supplied with 
	 *        atoms
	 * @throws QueueModelException if an atom was not present in the registry
	 */
	private <P extends IHasAtomQueue<T>, Q extends QueueableModel, T extends QueueAtom> void populateAtomQueue(Q model, P queueHolder) throws QueueModelException {
		for (String stShrtNm : model.getQueueAtomShortNames()) {
			try {
				T at = getQueueAtom(stShrtNm);
				queueHolder.addAtom(at);
			} catch (QueueModelException qme) {
				logger.error("Could not assemble SubTaskAtom due to missing child atom: "+qme.getMessage());
				throw new QueueModelException("Could not assemble SubTaskAtom: "+qme.getMessage(), qme);
			}
		}
	}

	@Override
	public void setDefaultTaskBeanModel(String reference) {
		defaultTaskBeanShortName = reference;
		explicitDefaultTaskBean = true;
	}

	@Override
	public String getDefaultTaskBeanModelName() throws QueueModelException {
		if (defaultTaskBeanShortName == null) {
			logger.error("No default TaskBeanModel set");
			throw new QueueModelException("No default TaskBeanModel set");
		}
		return defaultTaskBeanShortName;
	}

}
