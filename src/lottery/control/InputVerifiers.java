protected package lottery.control;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.WrongNetworkException;

import lottery.transaction.ComputeTx;
import lottery.transaction.LotteryTx;
import lottery.transaction.OpenTx;


public class InputVerifiers {

	public static class WrongInputException extends Exception {
		private static final long serialVersionUID = 1L;
		public WrongInputException(String string) {
			super(string);
		}
	}
	
	public static interface GenericVerifier<T> {
		public T verify(String input) throws WrongInputException;
	}
	
	protected static class BtcAmountVerifier implements GenericVerifier<BigInteger> {
		protected String type;
		protected BigInteger min;
		protected BigInteger max;
		
		public BtcAmountVerifier(String type, BigInteger min, BigInteger max) {
			this.type = type;
			this.min = min;
			this.max = max;
		}
		
		public BigInteger verify(String input) throws WrongInputException {
			try {
				BigInteger value = Utils.toNanoCoins(input);
				if (min != null && value.compareTo(min) < 0) {
					throw new WrongInputException(type + " has to be not smaller than " + Utils.bitcoinValueToFriendlyString(min) + "BTC.");
				}
				if (max != null && value.compareTo(max) > 0) {
					throw new WrongInputException(type + " has to be not greater than " + Utils.bitcoinValueToFriendlyString(max) + "BTC.");
				}
				return value;
			} catch (NumberFormatException e) {
				throw new WrongInputException("Wrong format of the " + type + ".");
			} catch (ArithmeticException e) {
				throw new WrongInputException("Wrong value of the " + type + ".");
			}
        }
    }

	public static class FeeVerifier extends BtcAmountVerifier {
		public FeeVerifier(BigInteger max) {
			super("fee", null, max);
		}
	}

	public static class StakeVerifier extends BtcAmountVerifier {
		public StakeVerifier(BigInteger min) {
			super("stake", min, null);
		}
	}
	
	public static class SkVerifier implements GenericVerifier<ECKey> {
		protected NetworkParameters params;
		protected byte[] pkHash;
		
		public SkVerifier(byte[] pkHash, boolean testnet) {
			params = LotteryTx.getNetworkParameters(testnet);
			this.pkHash = pkHash;
		}
		
		public ECKey verify(String input) throws WrongInputException {
			try {
				ECKey sk = new DumpedPrivateKey(params, input).getKey();
				if (pkHash != null && !Arrays.equals(sk.getPubKeyHash(), pkHash)) {
					throw new WrongInputException("The secret key does not correspond expected public key.");
				}
				return sk;
			} catch (AddressFormatException e) {
				throw new WrongInputException("Wrong format of the secret key.");
			} 
        }
    }
	
	public static class AddressVerifier implements GenericVerifier<Address> {
		protected NetworkParameters params;
		
		public AddressVerifier(boolean testnet) {
			params = LotteryTx.getNetworkParameters(testnet);
		}
		
		public Address verify(String input) throws WrongInputException {
			try {
				return new Address(params, input);
			} catch (WrongNetworkException e) {
				throw new WrongInputException("Provided key corresponds to a different chain.");
			} catch (AddressFormatException e) {
				throw new WrongInputException("Wrong format of the address.");
			} 
        }
    }
	
	protected static class NumberVerifier implements GenericVerifier<Long> {
		protected long min, max;
		protected String type;
		
		public NumberVerifier(long min, long max, String type) {
			this.min = min;
			this.max = max;
			this.type = type;
		}

		public Long verify(String input) throws WrongInputException {
			try {
				Long value = Long.parseLong(input);
				if (value < min) {
					throw new WrongInputException(type + " has to be not smaller than " + min + ".");
				}
				if (value > max) {
					throw new WrongInputException(type + " has to be not greater than " + max + ".");
				}
				return value;
			} catch (NumberFormatException e) {
				throw new WrongInputException("Wrong format of the " + type + ".");
			}
        }
    }
	
	public static class LockTimeVerifier extends NumberVerifier {
		public LockTimeVerifier() {
			super(1, 7 * 60 * 24, "lock time");
		}
    }
	
	public static class StartTimeVerifier extends NumberVerifier {
		public StartTimeVerifier() {
			super(Transaction.LOCKTIME_THRESHOLD, Long.MAX_VALUE, "start time");
		}
    }
	
	public static class MinLengthVerifier extends NumberVerifier {
		public MinLengthVerifier() {
			super(0, 512, "min length");
		}
    }
	
	public static class NoPlayersVerifier extends NumberVerifier {
		public NoPlayersVerifier() {
			super(2, 100, "number of players");
		}
    }
	
	public static class NewSecretVerifier implements GenericVerifier<byte[]> {
		protected int minLength, noPlayers;
		
		public NewSecretVerifier(int minLength, int noPlayers) {
			this.minLength = minLength;
			this.noPlayers = noPlayers;
		}

		public byte[] verify(String input) throws WrongInputException {
			if (input.equals("")) {
				return sampleSecret();
			}
			else {
				byte[] secret = Utils.parseAsHexOrBase58(input);
				if (secret == null) {
					throw new WrongInputException("Wrong format of the secret.");
				}
				if (secret.length < minLength) {
					throw new WrongInputException("The secret is to short.");
				}
				else if (secret.length >= minLength + noPlayers) {
					throw new WrongInputException("The secret is to long.");
				}
			}
			return null;
        }

		protected byte[] sampleSecret() {
		    SecureRandom random = new SecureRandom();
		    int n = random.nextInt(noPlayers); 	//TODO: is it secure?
		    byte[] secret = new byte[minLength + n];
			random.nextBytes(secret);
			return secret;
		}
    }
	
	public static class TxOutputVerifier implements GenericVerifier<TransactionOutput> {
		protected byte[] pkHash;
		protected NetworkParameters params;
		protected BigInteger value;
		protected int outNr;
		
		public TxOutputVerifier(ECKey sk, BigInteger value, boolean testnet) {
			this.pkHash = sk.getPubKeyHash();
			this.value = value;
			params = LotteryTx.getNetworkParameters(testnet);
		}

		public int getOutNr() {
			return outNr;
		}
		
		public TransactionOutput verify(String input) throws WrongInputException {
			//TODO: online verify: exists, not spent ?
			try {
				byte[] rawTx = Utils.parseAsHexOrBase58(input);
				if (rawTx == null) {
					throw new WrongInputException("Wrong format of the transaction.");
				}
				Transaction tx = new Transaction(params, rawTx);
				for (int k = 0; k < tx.getOutputs().size(); ++k) {
					TransactionOutput out = tx.getOutput(k);
                    try {
						if (out.equals(value) && Arrays.equals(out.getScriptPubKey().getPubKey(), pkHash)) {
							outNr = k;
							return tx.getOutput(k);
						}
					} catch (ScriptException e) {
						//do nothing - just ignore this output 
					}
	            }
	            throw new WrongInputException("No output available of the expected value for the given secret key.");
			} catch (ProtocolException e) {
				throw new WrongInputException("Wrong format of the transaction.");
			}
        }
    }
	
	protected static class TxVerifier {
		protected boolean testnet;
		protected Class<?> txClass;
		
		public TxVerifier(Class<?> txClass, boolean testnet) {
			this.testnet = testnet;
			this.txClass = txClass;
		}

		public LotteryTx verify(String input) throws WrongInputException {
			byte[] rawTx = Utils.parseAsHexOrBase58(input);
			if (rawTx == null) {
				throw new WrongInputException("Wrong format of the transaction.");
			}
			try {
				if (txClass == OpenTx.class) {
					return new OpenTx(rawTx, testnet);
				}
				else if (txClass == ComputeTx.class) {
					return new ComputeTx(rawTx, testnet);
				}
				else {
					throw new WrongInputException("");
				}
			} catch (VerificationException e) {
				throw new WrongInputException(e.getMessage());
			}
		}
	}
	
	public static class ComputeTxVerifier extends TxVerifier implements GenericVerifier<ComputeTx> {
		public ComputeTxVerifier(boolean testnet) {
			super(ComputeTx.class, testnet);
		}

		@Override
		public ComputeTx verify(String input) throws WrongInputException {
			return (ComputeTx) super.verify(input);
		}
	}
	
	public static class OpenTxVerifier extends TxVerifier implements GenericVerifier<OpenTx> {
		public OpenTxVerifier(boolean testnet) {
			super(OpenTx.class, testnet);
		}

		@Override
		public OpenTx verify(String input) throws WrongInputException {
			return (OpenTx) super.verify(input);
		}
	}
	
	public static class PkListVerifier implements GenericVerifier<byte[]> {
		protected byte[] expectedPk;
		protected int noPlayers;
		protected int counter;
		protected boolean pkPresent;
		protected int position;
		
		public PkListVerifier(byte[] expectedPk, int noPlayers) {
			this.expectedPk = expectedPk;
			this.noPlayers = noPlayers;
			this.counter = 0;
			this.pkPresent = false;
		}

		public int getPosition() {
			return position;
		}
		
		@Override
		public byte[] verify(String input) throws WrongInputException {
			if (counter >= noPlayers) {
				throw new WrongInputException("To many public keys.");
			}
			byte[] pk = Utils.parseAsHexOrBase58(input);
			if (pk == null) {
				throw new WrongInputException("Wrong format of the public key.");
			}
			if (Arrays.equals(pk, expectedPk)) {
				pkPresent = true;
				position = counter;
			}
			if (counter == noPlayers - 1 && !pkPresent) {
				throw new WrongInputException("Public key matching the provided secret key should be present.");
			}
			counter++;
			return pk;
		}
	}
	
	public static class SecretListVerifier implements GenericVerifier<byte[]> {
		protected List<byte[]> hashes;
		protected int minLength;
		protected int noPlayers;
		protected int counter;
		
		public SecretListVerifier(List<byte[]> hashes, int minLength) {
			this.hashes = hashes;
			this.minLength = minLength;
			this.noPlayers = hashes.size();
			this.counter = 0;
		}

		@Override
		public byte[] verify(String input) throws WrongInputException {
			if (counter >= noPlayers) {
				throw new WrongInputException("To many secrets.");
			}
			byte[] secret = Utils.parseAsHexOrBase58(input);
			if (secret == null) {
				throw new WrongInputException("Wrong format of the secret.");
			}
			if (secret.length < minLength) {
				throw new WrongInputException("The secret is to short.");
			}
			else if (secret.length >= minLength + noPlayers) {
				throw new WrongInputException("The secret is to long.");
			}
			else if (!Arrays.equals(LotteryUtils.calcHash(secret), hashes.get(counter))) {
				throw new WrongInputException("The secret does not match the hash.");
			}
			counter++;
			return secret;
		}
	}
}