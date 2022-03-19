import { TestBed } from '@angular/core/testing';

import { OnlyClientUsersService } from './only-client-users.service';

describe('OnlyClientUsersService', () => {
  let service: OnlyClientUsersService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnlyClientUsersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
