/* Copyright 2004 The Apache Software Foundation or its licensors, as applicable
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.platform.struct;


import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract implementation for the OS memory spies.
 * 
 */
abstract class AbstractMemorySpy implements IMemorySpy {

	// TODO: figure out how to prevent this being a synchronization bottleneck
	protected Map memoryInUse = new HashMap(); // Shadow to Wrapper

	protected Map refToShadow = new HashMap(); // Reference to Shadow

	protected ReferenceQueue notifyQueue = new ReferenceQueue();

	protected Object lock = new Object();

	final class AddressWrapper {
		final PlatformAddress shadow;

		final long size;

		final WeakReference wrAddress;

		volatile boolean autoFree = false;

		AddressWrapper(PlatformAddress address, long size) {
			super();
			this.shadow = PlatformAddress.on(address);
			this.size = size;
			this.wrAddress = new WeakReference(address, notifyQueue);
		}
	}

	public AbstractMemorySpy() {
		super();
	}

	public void alloc(PlatformAddress address, long size) {
		AddressWrapper wrapper = new AddressWrapper(address, size);
		synchronized (lock) {
			memoryInUse.put(wrapper.shadow, wrapper);
			refToShadow.put(wrapper.wrAddress, wrapper.shadow);
		}
	}

	public boolean free(PlatformAddress address) {
		AddressWrapper wrapper;
		synchronized (lock) {
			wrapper = (AddressWrapper) memoryInUse.remove(address);
		}
		if (wrapper == null) {
			// Attempt to free memory we didn't alloc
			System.err
					.println("Memory Spy! Fixed attempt to free memory that was not allocated " + address); //$NON-NLS-1$
		}
		return wrapper != null;
	}

	public void rangeCheck(PlatformAddress address, int offset, int length)
			throws IndexOutOfBoundsException {
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.platform.struct.IMemorySpy#autoFree(com.ibm.platform.struct.PlatformAddress)
	 */
	public void autoFree(PlatformAddress address) {
		AddressWrapper wrapper;
		synchronized (lock) {
			wrapper = (AddressWrapper) memoryInUse.remove(address);
		}
		if (wrapper != null) {
			wrapper.autoFree = true;
		}
	}

	protected void orphanedMemory(Object ref) {
		AddressWrapper wrapper;
		synchronized (lock) {
			Object shadow = refToShadow.remove(ref);
			wrapper = (AddressWrapper) memoryInUse.remove(shadow);
		}
		if (wrapper != null) {
			// There is a leak if we were not auto-freeing this memory.
			if (!wrapper.autoFree) {
				System.err
						.println("Memory Spy! Fixed memory leak by freeing " + wrapper.shadow); //$NON-NLS-1$
			}
			memoryInUse.put(wrapper.shadow, wrapper);
			wrapper.shadow.free();
		}

	}
}