import { TestBed } from '@angular/core/testing';

import { OnlyLoggedInService } from './only-logged-in.service';

describe('OnlyLoggedInService', () => {
  let service: OnlyLoggedInService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnlyLoggedInService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
