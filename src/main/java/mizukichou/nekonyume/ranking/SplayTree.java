package mizukichou.nekonyume.ranking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 伸展树（顺序统计变体）。
 *
 * <ul>
 * <li>查找/插入/删除摊还 O(log n)；</li>
 * <li>子树大小支持 1 基 {@link #select(int)} 与 {@link #rankOf(Object)}；</li>
 * <li>splay 与中序遍历均为迭代实现，深树不会栈溢出；</li>
 * <li>允许重复键（相等元素落右子树）；</li>
 * <li>非线程安全，仅限主线程使用；</li>
 * <li>比较器须为全序。</li>
 * </ul>
 *
 * @param <T> 元素类型
 */
public final class SplayTree<T> {

    private final Comparator<? super T> comparator;

    private Node root;
    private int size;

    /**
     * 以给定比较器构造空树。
     */
    public SplayTree(
            Comparator<? super T> comparator
    ) {

        this.comparator =
                Objects.requireNonNull(
                        comparator,
                        "comparator"
                );
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 插入元素；新节点随后 splay 到根。
     */
    public void insert(
            T value
    ) {

        Objects.requireNonNull(
                value,
                "value"
        );

        if (root == null) {

            root = new Node(
                    value
            );
            size = 1;
            return;
        }

        Node current = root;
        Node parent = null;

        while (current != null) {

            parent = current;

            int comparison =
                    comparator.compare(
                            value,
                            current.value
                    );

            if (comparison < 0) {
                current = current.left;
            } else {
                current = current.right;
            }
        }

        Node node = new Node(
                value
        );
        node.parent = parent;

        if (comparator.compare(
                value,
                parent.value
        ) < 0) {

            parent.left = node;
        } else {

            parent.right = node;
        }

        size++;
        splay(node);
    }

    /**
     * 是否包含某元素；命中时 splay 到根。
     */
    public boolean contains(
            T value
    ) {

        Objects.requireNonNull(
                value,
                "value"
        );

        return findNode(value) != null;
    }

    /**
     * 删除一个相等元素。
     */
    public boolean remove(
            T value
    ) {

        Objects.requireNonNull(
                value,
                "value"
        );

        Node node = findNode(value);

        if (node == null) {
            return false;
        }

        removeNode(node);
        return true;
    }

    /**
     * 第 k 小（1 基）元素。
     */
    public T select(
            int k
    ) {

        if (k < 1 || k > size) {

            throw new IndexOutOfBoundsException(
                    "select index "
                    + k
                    + " out of [1, "
                    + size
                    + "]"
            );
        }

        Node current = root;

        while (current != null) {

            int leftSize =
                    current.left == null
                    ? 0 : current.left.subtreeSize;

            if (k <= leftSize) {

                current = current.left;

            } else if (k == leftSize + 1) {

                splay(current);
                return current.value;

            } else {

                k -= leftSize + 1;
                current = current.right;
            }
        }

        //防御性：size 不变量被破坏时给出明确错误。
        throw new IllegalStateException(
                "select(): size invariant broken"
        );
    }

    /**
     * 1 基排名（严格小于该值的个数 + 1）；不存在返回 -1。
     * 计数沿搜索路径进行，重复键场景返回首个相等实例的位次。
     */
    public int rankOf(
            T value
    ) {

        Objects.requireNonNull(
                value,
                "value"
        );

        Node current = root;
        Node hit = null;
        int rank = 1;

        while (current != null) {

            int comparison =
                    comparator.compare(
                            value,
                            current.value
                    );

            if (comparison > 0) {

                rank += (current.left == null
                        ? 0 : current.left.subtreeSize) + 1;

                current = current.right;

            } else {

                if (comparison == 0) {
                    hit = current;
                }

                current = current.left;
            }
        }

        if (hit == null) {
            return -1;
        }

        splay(hit);
        return rank;
    }

    /**
     * 按比较器升序导出全部元素（不触发 splay）。
     */
    public List<T> toList() {

        List<T> result =
                new ArrayList<>(size);

        ArrayDeque<Node> stack =
                new ArrayDeque<>();

        Node current = root;

        while (current != null || !stack.isEmpty()) {

            while (current != null) {

                stack.push(current);
                current = current.left;
            }

            current = stack.pop();
            result.add(current.value);
            current = current.right;
        }

        return result;
    }

    /**
     * 把节点旋转到根（zig / zig-zig / zig-zag，迭代）。
     */
    private void splay(
            Node x
    ) {

        while (x.parent != null) {

            Node parent = x.parent;
            Node grandparent = parent.parent;

            if (grandparent == null) {

                if (parent.left == x) {
                    rotateRight(parent);
                } else {
                    rotateLeft(parent);
                }

            } else if (grandparent.left == parent) {

                if (parent.left == x) {

                    rotateRight(grandparent);
                    rotateRight(parent);

                } else {

                    rotateLeft(parent);
                    rotateRight(grandparent);
                }

            } else {

                if (parent.right == x) {

                    rotateLeft(grandparent);
                    rotateLeft(parent);

                } else {

                    rotateRight(parent);
                    rotateLeft(grandparent);
                }
            }
        }

        root = x;
        root.parent = null;
        update(root);
    }

    /**
     * 右旋。
     */
    private void rotateRight(
            Node y
    ) {

        Node x = y.left;

        if (x == null) {

            throw new IllegalStateException(
                    "rotateRight(): no left child"
            );
        }

        Node grandparent = y.parent;

        y.left = x.right;

        if (x.right != null) {
            x.right.parent = y;
        }

        x.right = y;
        y.parent = x;
        x.parent = grandparent;

        if (grandparent == null) {
            root = x;
        } else if (grandparent.left == y) {
            grandparent.left = x;
        } else {
            grandparent.right = x;
        }

        update(y);
        update(x);
    }

    /**
     * 左旋。
     */
    private void rotateLeft(
            Node y
    ) {

        Node x = y.right;

        if (x == null) {

            throw new IllegalStateException(
                    "rotateLeft(): no right child"
            );
        }

        Node grandparent = y.parent;

        y.right = x.left;

        if (x.left != null) {
            x.left.parent = y;
        }

        x.left = y;
        y.parent = x;
        x.parent = grandparent;

        if (grandparent == null) {
            root = x;
        } else if (grandparent.left == y) {
            grandparent.left = x;
        } else {
            grandparent.right = x;
        }

        update(y);
        update(x);
    }

    private void update(
            Node node
    ) {

        if (node == null) {
            return;
        }

        node.subtreeSize = 1
                + (node.left == null ? 0 : node.left.subtreeSize)
                + (node.right == null ? 0 : node.right.subtreeSize);
    }

    /**
     * 查找相等元素并 splay 到根；未找到返回 null。
     */
    private Node findNode(
            T value
    ) {

        Node current = root;

        while (current != null) {

            int comparison =
                    comparator.compare(
                            value,
                            current.value
                    );

            if (comparison == 0) {

                splay(current);
                return current;
            }

            current = comparison < 0
                    ? current.left : current.right;
        }

        return null;
    }

    /**
     * 删除节点；前置条件：node 是根。
     */
    private void removeNode(
            Node node
    ) {

        if (node != root) {

            throw new IllegalStateException(
                    "removeNode(): node is not root"
            );
        }

        if (node.left == null) {

            root = node.right;

        } else if (node.right == null) {

            root = node.left;

        } else {

            /*
             * 左子树最大值 splay 到全局根；
             * splay 后原 node 位于其右子树，
             * 直接把 node 的右子树挂到 maxLeft 右侧即可
             * 摘除 node 本身。
             */
            Node maxLeft = node.left;

            while (maxLeft.right != null) {
                maxLeft = maxLeft.right;
            }

            splay(maxLeft);

            maxLeft.right = node.right;

            if (node.right != null) {
                node.right.parent = maxLeft;
            }

            root = maxLeft;
        }

        if (root != null) {
            root.parent = null;
        }

        size--;
        update(root);
    }

    /** 包私有：供测试断言 splay 结果。 */
    T rootValue() {

        return root == null
                ? null : root.value;
    }

    /** 包私有：全树结构校验，供单元测试使用。 */
    void checkInvariants() {

        if (root == null) {

            if (size != 0) {

                throw new IllegalStateException(
                        "size=" + size
                        + " but tree is empty"
                );
            }

            return;
        }

        if (root.parent != null) {

            throw new IllegalStateException(
                    "root has a parent"
            );
        }

        int visited = 0;

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {

            Node node = stack.pop();

            if (node.parent == null && node != root) {

                throw new IllegalStateException(
                        "non-root node has null parent"
                );
            }

            if (node.left != null) {

                if (node.left.parent != node) {

                    throw new IllegalStateException(
                            "left child parent pointer broken"
                    );
                }

                if (comparator.compare(
                        node.left.value,
                        node.value
                ) > 0) {

                    throw new IllegalStateException(
                            "BST order violated on left child"
                    );
                }

                stack.push(node.left);
            }

            if (node.right != null) {

                if (node.right.parent != node) {

                    throw new IllegalStateException(
                            "right child parent pointer broken"
                    );
                }

                if (comparator.compare(
                        node.right.value,
                        node.value
                ) < 0) {

                    throw new IllegalStateException(
                            "BST order violated on right child"
                    );
                }

                stack.push(node.right);
            }

            int expected = 1
                    + (node.left == null ? 0 : node.left.subtreeSize)
                    + (node.right == null ? 0 : node.right.subtreeSize);

            if (node.subtreeSize != expected) {

                throw new IllegalStateException(
                        "subtreeSize broken: "
                        + node.subtreeSize
                        + " != "
                        + expected
                );
            }

            visited++;
        }

        if (visited != size) {

            throw new IllegalStateException(
                    "visited " + visited
                    + " nodes but size=" + size
            );
        }
    }

    private final class Node {

        private final T value;

        private Node left;
        private Node right;
        private Node parent;

        private int subtreeSize = 1;

        private Node(
                T value
        ) {

            this.value = value;
        }
    }
}
