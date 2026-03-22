import type { ApiEnvelope } from "@/models/types";

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<ApiEnvelope<T>> {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {})
    },
    ...options
  });
  const body = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok) {
    const message = (body.data as { message?: string })?.message ?? "Request failed.";
    throw new Error(message);
  }
  return body;
}

