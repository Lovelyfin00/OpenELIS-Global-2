package spring.service.qaevent;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import spring.service.common.BaseObjectServiceImpl;
import us.mn.state.health.lims.common.action.IActionConstants;
import us.mn.state.health.lims.common.exception.LIMSDuplicateRecordException;
import us.mn.state.health.lims.qaevent.dao.QaEventDAO;
import us.mn.state.health.lims.qaevent.valueholder.QaEvent;

@Service
public class QaEventServiceImpl extends BaseObjectServiceImpl<QaEvent, String> implements QaEventService {
	@Autowired
	protected QaEventDAO baseObjectDAO;

	QaEventServiceImpl() {
		super(QaEvent.class);
	}

	@Override
	protected QaEventDAO getBaseObjectDAO() {
		return baseObjectDAO;
	}

	@Override
	public void getData(QaEvent qaEvent) {
		getBaseObjectDAO().getData(qaEvent);

	}

	@Override
	public QaEvent getQaEventByName(QaEvent qaEvent) {
		return getBaseObjectDAO().getQaEventByName(qaEvent);
	}

	@Override
	public List getQaEvents(String filter) {
		return getBaseObjectDAO().getQaEvents(filter);
	}

	@Override
	public List getAllQaEvents() {
		return getBaseObjectDAO().getAllQaEvents();
	}

	@Override
	public Integer getTotalQaEventCount() {
		return getBaseObjectDAO().getTotalQaEventCount();
	}

	@Override
	public List getPageOfQaEvents(int startingRecNo) {
		return getBaseObjectDAO().getPageOfQaEvents(startingRecNo);
	}

	@Override
	public List getNextQaEventRecord(String id) {
		return getBaseObjectDAO().getNextQaEventRecord(id);
	}

	@Override
	public List getPreviousQaEventRecord(String id) {
		return getBaseObjectDAO().getPreviousQaEventRecord(id);
	}

	@Override
	// TODO csl confirm that this is correct
	public void delete(QaEvent qaEvent) {
		update(qaEvent, IActionConstants.AUDIT_TRAIL_DELETE);
	}

	@Override
	public String insert(QaEvent qaEvent) {
		if (duplicateQaEventExists(qaEvent)) {
			throw new LIMSDuplicateRecordException("Duplicate record exists for " + qaEvent.getQaEventName());
		}
		return super.insert(qaEvent);
	}

	@Override
	public QaEvent save(QaEvent qaEvent) {
		if (duplicateQaEventExists(qaEvent)) {
			throw new LIMSDuplicateRecordException("Duplicate record exists for " + qaEvent.getQaEventName());
		}
		return super.save(qaEvent);
	}

	@Override
	public QaEvent update(QaEvent qaEvent) {
		if (duplicateQaEventExists(qaEvent)) {
			throw new LIMSDuplicateRecordException("Duplicate record exists for " + qaEvent.getQaEventName());
		}
		return super.update(qaEvent);
	}

	private boolean duplicateQaEventExists(QaEvent qaEvent) {
		return baseObjectDAO.duplicateQaEventExists(qaEvent);
	}
}
