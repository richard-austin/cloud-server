import { TestBed } from '@angular/core/testing';

import { RxStompServiceService } from './rx-stomp-service.service';

describe('RxStompServiceService', () => {
  let service: RxStompServiceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(RxStompServiceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
