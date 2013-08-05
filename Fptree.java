import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Fptree {
	private static final float SUPPORT = 0.32f;
	private static long absSupport;

	public static void main(String[] args) {
		List<String[]> matrix = Reader.readAsMatrix("d.txt", "\t", "UTF-8");
		absSupport = (long) (SUPPORT * matrix.size());
		System.out.println("absSupport " + absSupport);
		Map<String, Integer> frequentMap = new LinkedHashMap<String, Integer>();// 一级频繁项
		Map<String, FpNode> header = getHeader(matrix, frequentMap);
		System.out.println(frequentMap);
		System.out.println(header);
		System.out.println(header.size());
		FpNode root = getFpTree(matrix, header, frequentMap);
		printTree(root);
		fpGrowth(root, header, null);
	}

	/**
	 * 用fp增长递归求平凡项
	 * 
	 * @param root
	 * @param header
	 */
	private static void fpGrowth(FpNode root, Map<String, FpNode> header,
			String idName) {
		Set<String> keys = header.keySet();
		String[] keysArray = keys.toArray(new String[0]);
		String firstIdName = keysArray[keysArray.length - 1];
		if (isSinglePath(header, firstIdName)) {
			if (idName == null)
				return;
			FpNode leaf = header.get(firstIdName);
			List<FpNode> paths = new ArrayList<FpNode>();// 自顶向上保存路径结点
			paths.add(leaf);
			FpNode node = leaf;
			while (node.parent.idName != null) {
				paths.add(node.parent);
				node = node.parent;
			}
			getCombinationPattern(paths, idName);
		} else {
			for (int i = keysArray.length - 1; i >= 0; i--) {
				String key = keysArray[i];
				List<FpNode> leafs = new ArrayList<FpNode>();
				FpNode link = header.get(key);
				while (link != null) {
					leafs.add(link);
					link = link.next;
				}
				Map<List<String>, Long> paths = new HashMap<List<String>, Long>();
				for (FpNode leaf : leafs) {
					List<String> path = new ArrayList<String>();
					FpNode node = leaf;
					while (node.parent.idName != null) {
						path.add(node.parent.idName);
						node = node.parent;
					}
					if (path.size() > 0)
						paths.put(path, leaf.count);
				}
				Holder holder = getConditionFpTree(paths);
				if (holder.header.size() != 0) {
					if (idName != null)
						key = idName + " " + key;
					fpGrowth(holder.root, holder.header, key);
				}
			}
		}

	}

	private static boolean isSinglePath(Map<String, FpNode> header,
			String tableLink) {
		if (header.get(tableLink).next == null)
			return true;
		return false;
	}

	/**
	 * 生成条件树
	 * 
	 * @param paths
	 * @return
	 */
	private static Holder getConditionFpTree(Map<List<String>, Long> paths) {
		List<String[]> matrix = new ArrayList<String[]>();
		for (Map.Entry<List<String>, Long> entry : paths.entrySet()) {
			for (long i = 0; i < entry.getValue(); i++) {
				matrix.add(entry.getKey().toArray(new String[0]));
			}
		}
		Map<String, Integer> frequentMap = new LinkedHashMap<String, Integer>();// 一级频繁项
		Map<String, FpNode> cHeader = getHeader(matrix, frequentMap);
		FpNode cRoot = getFpTree(matrix, cHeader, frequentMap);
		return new Holder(cRoot, cHeader);
	}

	/**
	 * 求单一路径上的所有组合加上idName构成的频繁项
	 * 
	 * @param paths
	 * @param idName
	 */
	private static void getCombinationPattern(List<FpNode> paths, String idName) {
		// System.out.println("paths "+paths);
		// System.out.println("idName " + idName);
		int size = paths.size();
		for (int mask = 1; mask < (1 << size); mask++) {
			List<FpNode> set = new ArrayList<FpNode>();
			// 找出每次可能的选择
			for (int i = 0; i < paths.size(); i++) {
				if ((mask & (1 << i)) > 0) {
					set.add(paths.get(i));
				}
			}
			StringBuilder builder = new StringBuilder();
			builder.append("[");
			long minValue = Long.MAX_VALUE;
			for (FpNode node : set) {
				builder.append(node.idName);
				builder.append(" ");
				if (node.count < minValue)
					minValue = node.count;
			}
			builder.append(idName);
			builder.append(" :");
			builder.append(minValue);
			builder.append("]");
			System.out.println(builder.toString());
		}
	}

	/**
	 * 打印fp树
	 * 
	 * @param root
	 */
	private static void printTree(FpNode root) {
		System.out.println(root);
		FpNode node = root.getChilde(0);
		System.out.println(node);
		for (FpNode child : node.children)
			System.out.println(child);
		System.out.println("*****");
		node = root.getChilde(1);
		System.out.println(node);
		for (FpNode child : node.children)
			System.out.println(child);

	}

	/**
	 * 构造FP树,同时利用方法的副作用更新表头
	 * 
	 * @param matrix
	 * @param header
	 * @param frequentMap
	 * @return 返回数的根结点
	 */
	private static FpNode getFpTree(List<String[]> matrix,
			Map<String, FpNode> header, Map<String, Integer> frequentMap) {
		FpNode root = new FpNode();
		int count = 0;
		for (String[] line : matrix) {
			String[] orderLine = getOrderLine(line, frequentMap);
			count++;
			if (count % 100000 == 0)
				System.out.println(count);
			FpNode parent = root;
			for (String idName : orderLine) {
				int index = parent.hasChild(idName);
				if (index != -1) {// 已经包含了该id，不需要新建结点
					parent = parent.getChilde(index);
					parent.addCount();
				} else {
					FpNode node = new FpNode(idName);
					parent.addChild(node);
					node.setParent(parent);
					FpNode nextNode = header.get(idName);
					if (nextNode == null) {// 表头还是空的，添加到表头
						header.put(idName, node);
					} else {// 添加的结点线索
						while (nextNode.next != null) {
							nextNode = nextNode.next;
						}
						nextNode.next = node;
					}
					parent = node;// 以后的结点挂在当前结点下面
				}
			}
			// System.out.println();
		}
		return root;
	}

	/**
	 * 将line数组里id按照frequentMap的值得降序排序
	 * 
	 * @param line
	 * @param frequentMap
	 * @return
	 */
	private static String[] getOrderLine(String[] line,
			Map<String, Integer> frequentMap) {
		Map<String, Integer> countMap = new HashMap<String, Integer>();
		for (String idName : line) {
			if (frequentMap.containsKey(idName)) {// 过滤掉非一级频繁项
				countMap.put(idName, frequentMap.get(idName));
			}
		}
		List<Map.Entry<String, Integer>> mapList = new ArrayList<Map.Entry<String, Integer>>(
				countMap.entrySet());
		Collections.sort(mapList, new Comparator<Map.Entry<String, Integer>>() {// 降序排序
					@Override
					public int compare(Entry<String, Integer> v1,
							Entry<String, Integer> v2) {
						return v2.getValue() - v1.getValue();
					}
				});
		String[] orderLine = new String[countMap.size()];
		int i = 0;
		for (Map.Entry<String, Integer> entry : mapList) {
			orderLine[i] = entry.getKey();
			i++;
		}
		return orderLine;
	}

	/**
	 * 生成表头
	 * 
	 * @param matrix
	 *            整个记录
	 * @return header 表头的键为id号，并且按照出现次数的降序排序
	 */
	private static Map<String, FpNode> getHeader(List<String[]> matrix,
			Map<String, Integer> frequentMap) {
		Map<String, Integer> countMap = new HashMap<String, Integer>();
		for (String[] line : matrix) {
			for (String idName : line) {
				if (countMap.containsKey(idName)) {
					countMap.put(idName, countMap.get(idName) + 1);
				} else {
					countMap.put(idName, 1);
				}
			}
		}
		for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
			if (entry.getValue() >= absSupport)// 过滤掉不满足支持度的项
				frequentMap.put(entry.getKey(), entry.getValue());
		}
		List<Map.Entry<String, Integer>> mapList = new ArrayList<Map.Entry<String, Integer>>(
				frequentMap.entrySet());
		Collections.sort(mapList, new Comparator<Map.Entry<String, Integer>>() {// 降序排序
					@Override
					public int compare(Entry<String, Integer> v1,
							Entry<String, Integer> v2) {
						return v2.getValue() - v1.getValue();
					}
				});
		frequentMap.clear();// 清空，以便保持有序的键值对
		Map<String, FpNode> header = new LinkedHashMap<String, FpNode>();
		for (Map.Entry<String, Integer> entry : mapList) {
			header.put(entry.getKey(), null);
			frequentMap.put(entry.getKey(), entry.getValue());
		}
		return header;
	}
}

class Holder {
	public final FpNode root;
	public final Map<String, FpNode> header;

	public Holder(FpNode root, Map<String, FpNode> header) {
		this.root = root;
		this.header = header;
	}
}
