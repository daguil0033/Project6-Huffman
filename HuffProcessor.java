// author: Daniel Aguilar (dba13)
import java.util.PriorityQueue;

/**
 * Spring 2020
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 *
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}


//HUFF DECOMPRESS
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 * @param in - Buffered bit stream of the file to be decompressed.
	 * @param out - Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTree(in);
		readBits(root, in, out);
		out.close();
	}

	/**
	 * Read the tree used to decompress
	 * @param in - Buffered bit stream of the file to be decompressed.
	 */
	private HuffNode readTree(BitInputStream in){
		int bits = in.readBits(1);
		if (bits == -1) throw new HuffException("failed when reading "+bits);
		if (bits == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
	}

	/**
	 * Read the bits from the compressed file
	 * @param in - Buffered bit stream of the file to be decompressed.
	 * @param out - Buffered bit stream writing to the output file.
	 * @param root - Huffnode root assigned as current node in the hufftree
	 */
	private void readBits(HuffNode root, BitInputStream in, BitOutputStream out){
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myRight == null && current.myLeft == null) {
					if (current.myValue == PSEUDO_EOF)
						break;   // out of loop
					else { out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
	}


//HUFF COMPRESS
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 * @param in - Buffered bit stream of the file to be compressed.
	 * @param out - Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] freq = readForCounts(in);
		HuffNode root = makeTreeFromCounts(freq);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	/**
	 * Determine the frequency of every eight-bit character/chunk in the file being compressed.
	 * @param in - Buffered bit stream of the file to be decompressed.
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[PSEUDO_EOF] = 1;
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				break;
			}
			else {
				freq[bits] = freq[bits]+1;
			}
		}
		return freq;
	}

	/**
	 * From the frequencies given in readForCounts, creates the Huffman trie/tree used to create encodings
	 * @param freq - frequencies of each eight-bit character
	 */
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i < freq.length; i++) {
			pq.add(new HuffNode(i, freq[i], null, null));
		}
		if(myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes\n", pq.size());
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();

		return root;
	}

	/**
	 * Creates pathway of unencoded left/right moves via 0s and 1s
	 * or adds encoding from leaf node to array
	 * @param encodings - encodings initialized in makeCodingsFromTree
	 * @param tree - hufftree
	 * @param path - path of left and right links presented by 0s and 1s
	 */
	private void codingHelper(String[] encodings, HuffNode tree, String path) {
		if(tree == null) return;
		if(tree.myLeft == null && tree.myRight == null) {
			encodings[tree.myValue] = path;
			return;
		} else {
			codingHelper(encodings, tree.myLeft, path+"0");
			codingHelper(encodings, tree.myRight, path+"1");
		}
		return;
	}

	/**
	 * From the trie/tree, create the encodings for each eight-bit character chunk
	 * @param root - Huffnode root
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(encodings, root, "");
		if(myDebugLevel >= DEBUG_HIGH) System.out.println("encodings complete.");
		return encodings;
	}

	/**
	 * Write the magic number and the tree to the beginning/header of the compressed file
	 * @param root - Huffnode root
	 * @param out - Buffered bit stream writing to the output file.
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft == null && root.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
		else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		if(myDebugLevel >= DEBUG_HIGH) System.out.println("header written.");
		return;
	}

	/**
	 * Re-reads and writes the encoding for each eight-bit char
	 * as well as the encoding for PSEUDO_EOF
	 * @param in - Buffered bit stream of the file to be compressed.
	 * @param out - Buffered bit stream writing to the output file.
	 * @param codings - encodings of 8-bit chars
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {

		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits == -1) {
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				break;
			}
			String code = codings[bits];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		if(myDebugLevel >= DEBUG_HIGH) System.out.println("compressed bits written.");
		return;
	}

}
