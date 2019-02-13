/*
 * Copyright 2011 Ytai Ben-Tsvi. All rights reserved.
 *  
 * 
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL ARSHAN POURSOHI OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied.
 */
package ioio.lib.impl;


import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import ioio.lib.spi.Log;

class QueueInputStream extends InputStream {
	private enum State {
		OPEN, CLOSED, KILLED
	}


	private final PipedOutputStream pipeOut;
	private final PipedInputStream pipeIn;

	private boolean loggedClosedStream = false;


	QueueInputStream(){
		try{
			this.pipeOut = new PipedOutputStream();
			this.pipeIn = new PipedInputStream(pipeOut, 1 << 16);
		} catch (IOException e){
			throw new IllegalStateException("Piped stream constructors should not throw IOException", e);
		}
	}



	private State state_ = State.OPEN;

	@Override
	public int read() throws IOException {
		State state = getState();

		if (state == State.KILLED) {
			throw new IOException("Stream has been closed");
		}

		if (state == State.CLOSED && (pipeIn.available() == 0)) {
			return -1;
		}

		return pipeIn.read();
	}



	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0) {
			return 0;
		}

		State state = getState();

		if (state == State.KILLED) {
			throw new IOException("Stream has been closed");
		}

		if (state == State.CLOSED && (pipeIn.available() == 0)) {
			return -1;
		}

		return pipeIn.read(b, off, len);
	}


	public void write(byte[] data, int size) {
		State state = getState();

		if ((state == State.KILLED) || (state == State.CLOSED)){
			if (!loggedClosedStream)
				Log.e("QueueInputStream", "Stream has been " + (state == State.KILLED ? "killed" : "closed"));
			loggedClosedStream = true;
			return;
		}

		try{
			pipeOut.write(data, 0, size);
			pipeOut.flush(); // It's retarded, but PipedOutputStream will not wake up the reader until flushed
		} catch (IOException e){
			Log.e("QueueInputStream", "PipedOutputStream.write should not throw an IOException");
		}
	}

	@Override
	public int available() throws IOException {
		return pipeIn.available();
	}

	@Override
	public void close() {
		if (getState() != State.OPEN) {
			return;
		}
		setState(State.CLOSED);
		try{
			pipeIn.close();
			pipeOut.close();
		} catch (IOException e){
			Log.e("QueueInputStream", "Piped(Input|Output)Stream.close should not throw an IOException");
		}
	}

	void kill() {
		if (getState() != State.OPEN) {
			return;
		}
		setState(State.KILLED);
		try{
			pipeIn.close();
			pipeOut.close();
		} catch (IOException e){
			Log.e("QueueInputStream", "Piped(Input|Output)Stream.close should not throw an IOException");
		}
	}


	private synchronized void setState(State state){
		this.state_ = state;
	}


	private synchronized State getState(){
		return state_;
	}

}
