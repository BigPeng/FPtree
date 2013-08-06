import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Fptree {
	private static final float SUPPORT = 0.6f;
	private static long absSupport;

	public static void main(String[] args) {
		List<String[]> matrix = Reader.readAsMatrix("d.txt", "\t", "utf-8");
		absSupport = (long) (SUPPORT * matrix.size());
		System.out.println("绝对支持度： " + absSupport);
		System.out.println("频繁项集： ");
		Map<String, Integer> frequentMap = new LinkedHashMap<String, Integer>();// 一级频繁项
		Map<String, FpNode> header = getHeader(matrix, frequentMap);
		FpNode root = getFpTree(matrix, header, frequentMap);
		// printTree(root);
		Map<Set<FpNode>, Long> frequents = fpGrowth(root, header, null);
		for (Map.Entry<Set<FpNode>, Long> fre : frequents.entrySet()) {
			for (FpNode node : fre.getKey())
				System.out.print(node.idName + " ");
			System.out.println("\t" + fre.getValue());
		}
	}

	/**
	 * 用fp增长递归求平凡项
	 * 
	 * @param root
	 * @param header
	 */
	private static Map<Set<FpNode>, Long> fpGrowth(FpNode root,
			Map<String, FpNode> header, String idName) {
		Map<Set<FpNode>, Long> conditionFres = new HashMap<Set<FpNode>, Long>();
		Set<String> keys = header.keySet();
		String[] keysArray = keys.toArray(new String[0]);
		String firstIdName = keysArray[keysArray.length - 1];
		if (isSinglePath(header, firstIdName)) {// 只有一条路径时，求路径上的所有组合即可得到调节频繁集
			if (idName == null)
				return conditionFres;
			FpNode leaf = header.get(firstIdName);
			List<FpNode> paths = new ArrayList<FpNode>();// 自顶向上保存路径结点
			paths.add(leaf);
			FpNode node = leaf;
			while (node.parent.idName != null) {
				paths.add(node.parent);
				node = node.parent;
			}
			conditionFres = getCombinationPattern(paths, idName);
			FpNode tempNode = new FpNode(idName, -1L);
			conditionFres = addLeafToFrequent(tempNode, conditionFres);

		} else {
			for (int i = keysArray.length - 1; i >= 0; i--) {// 递归求条件树的频繁集
				String key = keysArray[i];
				List<FpNode> leafs = new ArrayList<FpNode>();
				FpNode link = header.get(key);
				while (link != null) {
					leafs.add(link);
					link = link.next;
				}
				Map<List<String>, Long> paths = new HashMap<List<String>, Long>();
				Long leafCount = 0L;
				FpNode noParentNode = null;
				for (FpNode leaf : leafs) {
					List<String> path = new ArrayList<String>();
					FpNode node = leaf;
					while (node.parent.idName != null) {
						path.add(node.parent.idName);
						node = node.parent;
					}
					leafCount += leaf.count;
					if (path.size() > 0)
						paths.put(path, leaf.count);
					else {// 没有父结点
						noParentNode = leaf;
					}
				}
				if (noParentNode != null) {
					Set<FpNode> oneItem = new HashSet<FpNode>();
					oneItem.add(noParentNode);
					if (idName != null)
						oneItem.add(new FpNode(idName, -2));
					conditionFres.put(oneItem, leafCount);
				}
				Holder holder = getConditionFpTree(paths);
				if (holder.header.size() != 0) {
					// if (idName != null)
					// key = idName + " " + key;
					Map<Set<FpNode>, Long> preFres = fpGrowth(holder.root,
							holder.header, key);
					if (idName != null) {
						FpNode tempNode = new FpNode(idName, leafCount);
						preFres = addLeafToFrequent(tempNode, preFres);
					}
					conditionFres.putAll(preFres);
				}
			}
		}
		return conditionFres;

	}

	/**
	 * 将叶子结点添加到频繁集中
	 * 
	 * @param leaf
	 * @param conditionFres
	 */
	private static Map<Set<FpNode>, Long> addLeafToFrequent(FpNode leaf,
			Map<Set<FpNode>, Long> conditionFres) {
		if (conditionFres.size() == 0) {
			Set<FpNode> set = new HashSet<FpNode>();
			set.add(leaf);
			conditionFres.put(set, leaf.count);
		} else {
			Set<Set<FpNode>> keys = new HashSet<Set<FpNode>>(
					conditionFres.keySet());
			for (Set<FpNode> set : keys) {
				Long count = conditionFres.get(set);
				conditionFres.remove(set);
				set.add(leaf);
				conditionFres.put(set, count);
			}
		}
		return conditionFres;
	}

	/**
	 * 判断一颗fptree是否为单一路径
	 * 
	 * @param header
	 * @param tableLink
	 * @return
	 */
	private static boolean isSinglePath(Map<String, FpNode> header,
			String tableLink) {
		if (header.size() == 1 && header.get(tableLink).next == null)
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
	 * @return
	 */
	private static Map<Set<FpNode>, Long> getCombinationPattern(
			List<FpNode> paths, String idName) {
		Map<Set<FpNode>, Long> conditionFres = new HashMap<Set<FpNode>, Long>();
		int size = paths.size();
		for (int mask = 1; mask < (1 << size); mask++) {// 求所有组合，从1开始表示忽略空集
			Set<FpNode> set = new HashSet<FpNode>();
			// 找出每次可能的选择
			for (int i = 0; i < paths.size(); i++) {
				if ((mask & (1 << i)) > 0) {
					set.add(paths.get(i));
				}
			}
			long minValue = Long.MAX_VALUE;
			for (FpNode node : set) {
				if (node.count < minValue)
					minValue = node.count;
			}
			conditionFres.put(set, minValue);
		}
		return conditionFres;
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
//			count++;
//			if (count % 100000 == 0)
//				System.out.println(count);
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

/**
 * 
 * 生成条件树用到的包装器
 * 
 */
class Holder {
	public final FpNode root;
	public final Map<String, FpNode> header;

	public Holder(FpNode root, Map<String, FpNode> header) {
		this.root = root;
		this.header = header;
	}
}