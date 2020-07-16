package jsettlers.algorithms.simplebehaviortree;

import java.io.Serializable;
import java.util.List;

import java8.util.Lists2;

public abstract class Node<T> implements Serializable {
	private static final long serialVersionUID = -4544227752720944971L;

	private int     id;
	private boolean isOpen = false;

	public int getId() { return id; }

	protected final List<Node<T>> children;

	@SafeVarargs
	protected Node(Node<T>... children) {
		this.children = Lists2.of(children);
	}

	public NodeStatus execute(Tick<T> tick) {
		if (!isOpen) {
			open(tick);
		}
		enter(tick);
		NodeStatus status = this.tick(tick);
		exit(tick);
		if (status != NodeStatus.RUNNING) {
			close(tick);
		}
		return status;
	}

	private void enter(Tick<T> tick) {
		tick.visitNode(this);
		onEnter(tick);
	}

	private void open(Tick<T> tick) {
		isOpen = true;
		onOpen(tick);
	}

	private NodeStatus tick(Tick<T> tick) {
		tick.tickNode(this);
		return onTick(tick);
	}

	public void close(Tick<T> tick) {
		if (isOpen) {
			tick.leaveNode(this);
			isOpen = false;
			onClose(tick);
		}
	}

	private void exit(Tick<T> tick) {
		onExit(tick);
	}

	protected void onEnter(Tick<T> tick) { }

	protected void onOpen(Tick<T> tick) { }

	protected NodeStatus onTick(Tick<T> tick) {
		return NodeStatus.SUCCESS;
	}

	protected void onClose(Tick<T> tick) { }

	protected void onExit(Tick<T> tick) { }

	protected int initiate(int maxId) {
		maxId++;
		this.id = maxId;
		for (Node<T> child : children) {
			maxId = child.initiate(maxId);
		}
		return maxId;
	}
}
