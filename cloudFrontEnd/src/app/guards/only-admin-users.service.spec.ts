import { TestBed } from '@angular/core/testing';

import { OnlyAdminUsersService } from './only-admin-users.service';

describe('OnlyAdminUsersService', () => {
  let service: OnlyAdminUsersService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnlyAdminUsersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
