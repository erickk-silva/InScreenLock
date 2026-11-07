import { useSyncExternalStore } from 'react';

let cached: boolean | undefined;

/** Probes WebGL once, without logging, so the 3D chunk is only mounted when a context can really be created. */
export function webglAvailable(): boolean {
  if (cached !== undefined) return cached;
  try {
    const probe = document.createElement('canvas');
    const gl = (probe.getContext('webgl2') ?? probe.getContext('webgl')) as WebGLRenderingContext | WebGL2RenderingContext | null;
    cached = gl !== null;
    gl?.getExtension('WEBGL_lose_context')?.loseContext();
  } catch {
    cached = false;
  }
  return cached;
}

const noopSubscribe = () => () => undefined;
/** `null` while rendering on the server and during hydration, then the real answer. */
export const useWebglSupport = (): boolean | null => useSyncExternalStore(noopSubscribe, webglAvailable, () => null);
