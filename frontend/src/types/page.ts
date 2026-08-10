export type PageResponse<T> = {
  content: T[];
  number?: number;
  page?: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
};

export function asPage<T>(
  response: PageResponse<T> | T[],
  requestedPage = 0,
  requestedSize = 20,
): PageResponse<T> {
  if (!Array.isArray(response)) {
    return {
      ...response,
      number: response.number ?? response.page ?? requestedPage,
    };
  }
  return {
    content: response,
    number: requestedPage,
    size: requestedSize,
    totalElements: response.length,
    totalPages: response.length ? 1 : 0,
    first: true,
    last: true,
  };
}
