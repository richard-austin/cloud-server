import { TestBed } from '@angular/core/testing';

import { OnlyAnonUsersService } from './only-anon-users.service';

describe('OnlyAnonUserService', () => {
  let service: OnlyAnonUsersService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnlyAnonUsersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
