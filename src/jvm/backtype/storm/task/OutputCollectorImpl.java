package backtype.storm.task;

import backtype.storm.tuple.MessageId;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.TupleImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;


//On tuple emit:
//
//we have: Map<Long, Long> for each anchor
//
//create map from root to id
//
//for each anchor, add a new unique id for its output (I)
//udpate root -> id with xor of I for each of anchor's roots
//
//this map is the message id map for the new tuple

public class OutputCollectorImpl extends OutputCollector {
    private TopologyContext _context;
    private IInternalOutputCollector _collector;
    private Map<Tuple, List<Long>> _pendingAcks = new ConcurrentHashMap<Tuple, List<Long>>();
    
    public OutputCollectorImpl(TopologyContext context, IInternalOutputCollector collector) {
        _context = context;
        _collector = collector;
    }
    
    public List<Integer> emit(int streamId, List<Tuple> anchors, List<Object> tuple) {
        return _collector.emit(anchorTuple(anchors, streamId, tuple));
    }
    
    public void emitDirect(int taskId, int streamId, List<Tuple> anchors, List<Object> tuple) {
        _collector.emitDirect(taskId, anchorTuple(anchors, streamId, tuple));
    }

    private Tuple anchorTuple(List<Tuple> anchors, int streamId, List<Object> tuple) {
        Map<Long, Long> anchorsToIds = new HashMap<Long, Long>();
        if(anchors!=null) {
            for(Tuple anchor: anchors) {
                long newId = MessageId.generateId();
                getExistingOutput(anchor).add(newId);
                for(long root: anchor.getMessageId().getAnchorsToIds().keySet()) {
                    Long curr = anchorsToIds.get(root);
                    if(curr == null) curr = 0L;
                    anchorsToIds.put(root, curr ^ newId);                
                }
            }
        }
        List<Integer> fullStreamId = new ArrayList<Integer>(1);
        fullStreamId.add(streamId);
        return new TupleImpl(_context, tuple, _context.getThisTaskId(), fullStreamId, null, MessageId.makeId(anchorsToIds));
    }

    public void ack(Tuple input) {
        List<Long> generated = getExistingOutput(input);
        _pendingAcks.remove(input); //don't just do this directly in case there was no output
        _collector.ack(input, generated);
    }

    public void fail(Tuple input) {
        _pendingAcks.remove(input);
        _collector.fail(input);
    }
    
    public void reportError(Throwable error) {
        _collector.reportError(error);
    }

    
    private List<Long> getExistingOutput(Tuple anchor) {
        if(_pendingAcks.containsKey(anchor)) {
            return _pendingAcks.get(anchor);
        } else {
            List<Long> ret = new ArrayList<Long>();
            _pendingAcks.put(anchor, ret);
            return ret;
        }
    }
}
