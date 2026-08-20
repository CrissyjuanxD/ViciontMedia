package com.vctmedia.util;

import net.minecraft.client.util.memory.ObjectAllocator;

/**
 * Provides access to GameRenderer's internal ObjectPool (which implements ObjectAllocator),
 * needed to run FrameGraphBuilder in 1.21.11's post-effect rendering.
 */
public interface GameRendererPoolAccessor {
    ObjectAllocator getPool();
}
