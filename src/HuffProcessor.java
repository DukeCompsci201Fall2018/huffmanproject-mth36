import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <p>
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
	public static final int HUFF_TREE = HUFF_NUMBER | 1;
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	private final int myDebugLevel;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] freqs = frequencies(in);
		HuffNode root = makeHuffTree(freqs);
		String[] codings = makeCodings(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHuffTree(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {

		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE)
			throw new HuffException("illegal header starts with " + bits);

		HuffNode root = readTree(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	/**
	 * counts the frequency of each character in the input stream and stores
	 * these frequencies in an array.
	 *
	 * @param in Buffered bit stream of the file to be decompressed.
	 * @return an array ints, whose values are the frequencies of its indices in the file.
	 */
	private int[] frequencies(BitInputStream in) {
		int[] freqs = new int[ALPH_SIZE + 1];
		freqs[PSEUDO_EOF] = 1; //explicitly set single occurrence of PSEUDO_EOF

		if (myDebugLevel >= DEBUG_HIGH){
			System.out.println("frequencies:");
		}

		while (true) {
			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) break; //end of input
			freqs[value] += 1; //increment frequency

		}

		if (myDebugLevel >= DEBUG_HIGH){
			for (int i=0; i<freqs.length; i++){
				if (freqs[i] != 0){
					System.out.println(i + "\t" + freqs[i]);
				}
			}
		}

		return freqs;
	}

	/**
	 * given an array of frequencies of characters,
	 * creates a huffman encoding tree
	 *
	 * @param freqs the array of frequencies used to create the tree
	 * @return the root of a HuffNode tree that encodes characters into bit sequences
	 */
	private HuffNode makeHuffTree(int[] freqs) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for (int i = 0; i < freqs.length; i++) {
			if (freqs[i] > 0) { //only make nodes for occurring characters
				pq.add(new HuffNode(i, freqs[i], null, null));
			}
		}
		if (myDebugLevel >= DEBUG_HIGH){
			System.out.println("huffman tree encoding:\npq created with " + pq.size() + " nodes");
		}

		//each loop will decrease the number of elements in the PQ by 1
		//i.e. removes 2 nodes and creates a single tree from them, which is added to PQ.
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}

		//only one node left in PQ, which is the fully-constructed huffman tree.
		return pq.remove();
	}

	/**
	 * given a huffman encoding tree,
	 * generate an array of the encodings for the
	 * characters in that tree
	 *
	 * @param root the root of a HuffNode tree that encodes characters into bit sequences
	 * @return an array of bit sequence encodings, whose indices are the characters encoded
	 */
	private String[] makeCodings(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];

		if (myDebugLevel >= DEBUG_HIGH){
			System.out.println("encodings from huffman tree:");
		}

		codingHelper(root, "", encodings);
		return encodings;
	}

	/**
	 * a recursive helper method for makeCodings()
	 *
	 * recursively builds each root-to-leaf path
	 * and stores it in the encodings array
	 *
	 * each root-to-leaf path is the encoding for
	 * the character stored in its leaf node.
	 *
	 * @param root the root of a HuffNode tree that encodes characters into bit sequences
	 * @param path the current path from the root to the current node
	 * @param encodings an array of encodings for the characters in the huffman tree
	 */
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft == null & root.myRight == null) {
			encodings[root.myValue] = path;
			if (myDebugLevel >= DEBUG_HIGH){
				System.out.println("encoding of " + root.myValue + " is " + path);
			}
		} else {
			if (root.myLeft != null) codingHelper(root.myLeft, path + "0", encodings);
			if (root.myRight != null) codingHelper(root.myRight, path + "1", encodings);
		}
	}

	/**
	 * writes the huffman tree (i.e. the HuffNode tree)
	 * to the given BitOutputStream
	 *
	 * the tree is written via pre-order traversal.
	 * each non-leaf is stored as a 0, each leaf is stored as a 1
	 * followed by the bit sequence representing its contained character
	 *
	 * @param root the root of a HuffNode tree that encodes characters into bit sequences
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void writeHuffTree(HuffNode root, BitOutputStream out) {
		//if root has children i.e. an internal node
		boolean isLeaf = root.myLeft == null && root.myRight == null;

		if (!isLeaf) {
			out.writeBits(1, 0);
			if (root.myLeft != null) writeHuffTree(root.myLeft, out);
			if (root.myRight != null) writeHuffTree(root.myRight, out);
		} else { //if isLeaf
			if (myDebugLevel >= DEBUG_HIGH){
				System.out.println("wrote leaf " + root.myValue);
			}
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}

	/**
	 * writes the file contained in the BitInputStream
	 * to a BitOutputStream,
	 * using the encodings generated from a huffman tree
	 * to compress the file.
	 *
	 * @param codings an array of encodings for characters into bit sequences
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		String code;

		while (true){
			int value = in.readBits(8);
			if (value == -1){
				break; //break out of loop once bit stream ends or is invalid.
			}
			code = codings[value];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			if (myDebugLevel >= DEBUG_HIGH){
				System.out.println("wrote " + code + " from " + value);
			}
		}

		//write the PSUEDO_EOF to signify end-of-file
		code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		if (myDebugLevel >= DEBUG_HIGH){
			System.out.println("wrote PSEUDO_EOF");
		}
	}

	/**
	 * recursively reads a huffman tree encoded via a pre-order
	 * traversal in a BitInputStream, and
	 * creates a HuffNode tree from the traversal
	 *
	 * @param in Buffered bit stream of the file to be compressed.
	 * @return root of a HuffNode tree generated from the pre-order traversal-encoded huffman tree
	 */
	private HuffNode readTree(BitInputStream in) {
		//read a single bit
		int bit = in.readBits(1);

		if (bit == -1) {
			throw new HuffException("bad input, invalid tree");
		}
		if (bit == 0) { //bit represents an internal non-leaf node
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		} else { //bit represents a leaf node: read its value, put into a new HuffNode
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}

	/**
	 * reads a huffman-encoded BitInputStream
	 * via a HuffNode tree / huffman tree
	 * and writes the decoding to a BitOutputStream
	 *
	 * @param root root of the HuffNode tree used to
	 * @param in Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode curr = root;

		while (true) {
			int bit = in.readBits(1);

			if (bit == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				//if the bit read is 0, traverse left. if 1, traverse right
				if (bit == 0) curr = curr.myLeft;
				else curr = curr.myRight;

				//if curr is a leaf node
				if (curr.myLeft == null && curr.myRight == null) {
					if (curr.myValue == PSEUDO_EOF)
						break;   // input stream is over, exit loop
					else {
						out.writeBits(BITS_PER_WORD, curr.myValue);
						curr = root; // start back after reaching leaf
					}
				}
			}
		}
	}
}